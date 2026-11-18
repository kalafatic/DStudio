# PHASE-7-UNIFIED-BACKEND-REPORT.md

**DStudio Phase 7 — S4 Unified Backend deliverable**
**Date:** 2026-11-19
**Status:** Implemented and verified. This report describes actual implemented behavior;
all evidence below was produced by running the code (build, tests, live headless server).

---

## 1. Scope

Stage S4 completed the backend so that every client (RCP, Web IDE, TEIP consumers)
operates on one authoritative, headless-capable core with:

1. Live event streaming (SSE) with reconnection replay.
2. Shared API authentication across every HTTP endpoint.
3. Encryption at rest for all secrets (API keys, tokens).
4. Persisted direct-inference sessions (multi-turn, streaming, cancellation).

No behavior was removed; TEIP 1.1 wire behavior is unchanged (verified by the existing
TEIP tests). No new frameworks were introduced (JDK `HttpServer` SSE, SQLite, Jackson as
before).

## 2. Implemented

### 2.1 Secrets: encryption at rest

- `SecretCodec` (infrastructure): AES/GCM/NoPadding, 12-byte IV per value, 128-bit tag.
  Master key = 32 random bytes in `<config-dir>/secrets.key`, owner-only permissions
  (600 on POSIX), generated on first use. Wire format `enc:<b64 iv>:<b64 ct>`.
- Sensitive-key detection: keys matching `token|secret|password|credential|api[_-]?key`
  are encrypted transparently on save and decrypted on load.
- `SqliteHarnessStore` and `SqliteInferenceEngineStore` now require a `SecretCodec` and
  apply it to `saveConfiguration`/`findConfiguration`. Verified by test: raw
  `config_json` in SQLite never contains the plaintext secret.
- Existing masking behavior preserved: DCode/ENR adapters still return `******` from
  `getConnectionConfiguration`; no API route returns credentials.

### 2.2 Global event stream (SSE)

- `EventStream`/`DefaultEventStream` (core): bounded ring buffer (10 000 entries)
  fed by a bus subscription; monotonic sequences; `after(seq)` replay and blocking
  `awaitNext(seq, timeout)`.
- `GET /api/events` (teip): chunked `text/event-stream`; frames carry
  `id:` (sequence), `event:` (payload type), `data:` (JSON). Reconnection contract:
  `?afterSequence=N` or `Last-Event-ID` header replays the buffered window first, then
  continues live. Heartbeat comment every 15 s. No cursor = live-only.
- The stream is a convenience channel, not a durable record: clients must reconcile
  against authoritative REST state after reconnecting (documented in the handler).
- Clean shutdown: `DStudioServer.stop()` ends every open SSE loop before stopping the
  HTTP server (verified: SIGTERM → process exit, port released, no stray threads).

### 2.3 Shared API authentication

- `ApiAuth` (teip): every `/api/*` context (`/api/status`, `/api/diagnostics`,
  `/api/inference`, `/api/events`) now enforces the same bearer token
  (`dstudio.teip.auth.token` / `DSTUDIO_TEIP_AUTH_TOKEN`, constant-time compare) with
  the same explicit dev-mode loopback opt-out (`dstudio.teip.devMode=true`) as TEIP.
  Denials return `401 {"code":"AUTHENTICATION_FAILED",...}`.
- Browser note: EventSource cannot set headers; cookie-based session auth is deferred
  to the Web IDE stage. Programmatic clients use the bearer header.

### 2.4 Direct-inference sessions

- Core: `InferenceSession` / `InferenceSessionMessage` models,
  `InferenceSessionStore` + `SqliteInferenceSessionStore` (schema V3:
  `inference_sessions`, `inference_messages`), `InferenceService` +
  `DefaultInferenceService`, `InferenceStreamEvent`, `InferenceException` (codes:
  NOT_FOUND, CONFLICT, VALIDATION_FAILED, INFERENCE_FAILED).
- Semantics: engine+model pinned at creation (never silently switched); system prompt
  + full history sent to the engine each turn; user and assistant messages persisted;
  failure marks the session FAILED with the engine's error; cancellation marks
  CANCELLED (idempotent, partial content preserved); terminal sessions reject new
  messages with 409.
- HTTP (extended `InferenceHttpHandler`):
  - `POST /api/inference/sessions` → 201
  - `GET /api/inference/sessions`, `GET /api/inference/sessions/{id}` (with messages)
  - `POST /api/inference/sessions/{id}/messages` → blocking answer
  - `POST /api/inference/sessions/{id}/messages/stream` → SSE `chunk`/`done`/`error`
  - `POST /api/inference/sessions/{id}/cancel` → idempotent
- Existing `/api/inference` routes (engines, models, load/unload, runtime, `/chat`)
  are unchanged.

### 2.5 Adapter fixes

- ENR and Ollama `streamChat` now honor `cancellationSupplier` by closing the HTTP
  body stream (aborts the exchange); a cancelled stream surfaces as CANCELLED, never
  FAILED. Ollama streaming errors now include the provider's error body.

## 3. Verification

| Evidence | Result |
|---|---|
| `mvn -o clean verify` (offline, Tycho 4.0.8) | **135/135 tests pass** |
| New unit/wire tests | SecretCodecTest 7, EventStreamTest 4, SseEventsApiTest 3, InferenceSessionServiceTest 7, InferenceSessionApiTest 7 |
| Live Ollama streaming | real `qwen3-coder-30b-32k:latest`, 632 ms, done chunk, session persisted (`OllamaLiveInferenceTest`) |
| Live Ollama cancellation | real stream aborted in 78 ms, session CANCELLED (`OllamaLiveInferenceTest`) |
| Headless product runtime | materialized product started on 48991; `/api/status` UP; SSE delivered `InferenceStreamEvent` (CREATED/CHUNK/DONE); session streamed "OK"/"NO" over HTTP SSE with 4-message persisted history; reconnection `afterSequence=1` replayed events 2-3; live HTTP cancel → CANCELLED; SIGTERM → clean exit, port released |
| TEIP regression | TeipApiEndpointTest 14, TeipOpenCodeEndToEndTest (live OpenCode E2E) pass unchanged |
| EVO MCP live test | now skips when port 28280 hosts a non-cognitive MCP server (dependency probe via `tools/list`) |

## 4. Known limitations (honest disclosure)

- Ollama `runtimeStatus()` still reports local JVM stats (not Ollama runtime data) —
  tracked for the S3 multi-runtime stage.
- SSE authentication for browsers requires cookie support (deferred to Web IDE stage).
- A client disconnect during an inference stream cancels the session (partial content
  is persisted). A cancel racing end-of-stream wins as CANCELLED.
- The event stream buffer is in-memory; events are not replayed across server restarts
  (REST state remains authoritative).
- ENR/Ollama live verification is against Ollama only; ENR has wire-level tests but no
  live runtime was available on this host.

## 5. Architectural notes

- Dependency direction preserved: core has no SWT/workbench/TEIP references; the TEIP
  bundle consumes core SPI only; the lifecycle coordinator remains the single wiring
  point for the shared core.
- One event system: the existing `EventBus` feeds both the (new) SSE channel and the
  existing RCP console subscription. No second event bus was introduced.
- One persistence: sessions live in the existing SQLite database (schema V3,
  forward-only migration), managed by the existing `SchemaMigrationService`.

## 6. Port family convention and conflict handling (S4 follow-up)

### 6.1 Ports

Family convention (EVO 28xxx → TRYST 38xxx → DStudio 48xxx → DCode 58xxx; additional
services step by +100 inside each block):

| Product | Primary port | Diagnostics / additional |
|---|---|---|
| EVO | 28080 | 28180, 28280 (MCP), 28380, 28480, 28580 |
| TRYST | 38080 | 38280, 38480 |
| DStudio | **48080** (TEIP/API/events) | **48180** (diagnostics, config-only) |
| DCode/DCoder | 58080 | 58180+ |

- `DStudioConfiguration.DEFAULT_TEIP_PORT` reverted to **48080** (a stray uncommitted
  48081 change had broken the convention); `DEFAULT_DIAGNOSTICS_PORT` = 48180.
- `DCodeHarnessAdapter.DEFAULT_ENDPOINT` corrected to **http://localhost:58080** — it
  previously pointed at DStudio's own TEIP port.

### 6.2 Port conflicts are interactive and actionable

- `DStudioServer.start()` maps `BindException` to a typed `PortConflictException`
  (address, port, remediation text). Never silently switches ports or configuration.
- **Desktop**: `DStudioApplication` catches it and shows an interactive
  `PortConflictDialog` — what happened, what the port is used for, what to fix
  (stop the conflicting process / choose another port / exit). The chosen free port
  (pre-filled via `PortProbe`) is retried and **persisted** to
  `~/.dstudio/dstudio.properties` through `restartServerOnPort()`, then the IDE
  continues normally. Any other startup failure ends in an actionable
  `ErrorDialog` with the failure details, never a bare "An error has occurred".
- **Headless**: prints actionable STARTUP FAILED guidance (port, occupying-process
  hints, exact config keys to change), preserves configuration, disposes services and
  exits promptly (non-zero).
- Stage-machine fix: `terminateAfterStartupFailure()` shuts down without re-running the
  failed `APPLICATION_READY` stage (a plain `advance(TERMINATED)` would retry the bind
  and rethrow).
- Launcher exit quirk (verified live): after a failed application start the embedded
  Eclipse launcher does not exit on `System.exit`/`Runtime.halt`, and
  `ProcessHandle.destroy()` is forbidden on the current process; `DStudioStartupExit`
  therefore terminates via SIGKILL to its own PID after disposal (prompt, deterministic).
- Verified live: occupied 48080 → guidance printed, config intact, exit in ~3.3 s;
  free 48080 → server binds the family port; conflict/restart/persistence covered by
  `PortConflictHandlingTest` (4 tests).

## 7. Stage 2 — Multi-harness completion (S2)

- **Claude Code harness** (`ClaudeCodeHarnessAdapter`, harness bundle): headless CLI
  contract (`claude --print --output-format stream-json --verbose [-p prompt]`),
  Anthropic-native provider type, cancellation, containment + changed-file collection.
  **Execution recipe NOT live-verified on this host (no `claude` binary installed) — the
  live test skips silently; run `ClaudeCodeHarnessAdapterTest.liveExecutionSkipsWithoutCli`
  on a host with Claude Code to complete verification.**
- **Harness/provider compatibility matrix foundation**: `HarnessCapabilityDescriptor`
  gained `supportedProviderTypes` — OpenCode: OLLAMA/OPENAI_COMPATIBLE; Aider:
  OLLAMA/OPENAI_COMPATIBLE/ANTHROPIC; DCode: DCODE_NATIVE; Claude: ANTHROPIC. Exposed
  per harness in `/api/diagnostics` so clients can hide incompatible combinations.
- Explicit selection wins: OpenCode/Aider now honor the per-task `request.model()`
  (and `request.agent()` for OpenCode) instead of silently overriding with config
  defaults; configured values remain fallback defaults only.

## 8. Stage 3 — Multi-runtime completion (S3)

- **OpenAI-compatible engine** (`OpenAiCompatibleInferenceEngineAdapter`): `/models`,
  `/chat/completions` blocking + streaming, cancellation (stream abort), single done
  emission, masked apiKey, encrypted-at-rest apiKey. **Live-verified against real
  Ollama `/v1`** (21 models listed, live chat and live streaming pass;
  `OpenAiCompatibleLiveTest`).
- **Anthropic engine** (`AnthropicInferenceEngineAdapter`): `/v1/models`,
  `/v1/messages` blocking + streaming, x-api-key/anthropic-version headers, system
  prompt mapping. Wire-tested against in-process stub; **live requires an API key —
  not executed on this host** (test skips without a key).
- **Multi-instance runtimes**: ENR/Ollama/OpenAI/Anthropic adapters accept
  (id, name) instance constructors; new `InferenceEngineFactory` SPI (core) implemented
  in the application bundle (dependency direction preserved); HTTP administration:
  `POST /api/inference/engines` (create instance, 409 on duplicate id, 422 on unknown
  type) and `DELETE /api/inference/engines/{id}` (permanent removal incl. persisted
  descriptor). Registry gained `removeEngine`. Two Ollama endpoints (local + GPU server)
  verified live via the API.
- No silent fallback remains: engines are pinned per session; failures mark sessions
  FAILED with the engine's error.

## 9. Verification totals (this stage)

- `mvn -o clean verify`: **154/154 tests pass** (was 139) — new: ClaudeCodeHarnessAdapterTest 4,
  OpenAiAnthropicAdapterWireTest 6, OpenAiCompatibleLiveTest 3 (live), EngineInstanceApiTest 2.
- Live: OpenAI-compatible blocking + streaming against real Ollama /v1; full multi-turn
  session through the `openai` engine ("OPENAI_OK"); instance create/duplicate/remove
  via HTTP on the headless product; 4 harnesses + 4 engines discoverable in diagnostics.
- Remaining platform stages: S5 RCP IDE professionalization, S6 Web IDE, S7 cross-client
  verification (not started).

## 10. Stage 5 — RCP IDE professionalization (S5)

- **Full standard menu bar** (`ApplicationActionBarAdvisor`): File (Save/Save As/Save
  All/Revert/Import/Export/Close/Close All/Quit), Edit (Undo/Redo/Cut/Copy/Paste/
  Delete/Select All), Navigate (Back/Forward history, Forward), Search (Find), Window
  (New Window/Reset Perspective/Preferences), Help (About) — all real workbench
  actions. **Project** and **Run** menus are contributed by the UI bundle via
  `org.eclipse.ui.menus` (layering preserved) with the working openProject/
  runHarness/cancelExecution/refreshProjects commands.
- **RunHarnessDialog**: hardcoded model list removed — models are now discovered from
  the registered inference engines and **filtered by the harness's supported provider
  types** (compatibility matrix, §3.5: incompatible combinations are never offered);
  free-text model entry remains for explicit provider/model values.
- GUI runtime verification requires a graphical host (this host has no X server):
  compile-verified + unit-verified; runtime acceptance remains to be executed on a
  desktop host.

## 11. Stage 6 — Interactive Web IDE (S6)

- **One backend serves the frontend**: embedded `web/` assets (index.html, app.js,
  style.css — vanilla JS, no external frameworks/CDN) served by `WebAssetsHandler` at
  `/` and `/web` from the existing `DStudioServer`; no second server.
- **Workspace/file API** (`/api/workspace`): project open/close/list;
  `GET /api/workspace/files` (directory listing / file read), `PUT` (save, creates new
  files); strict path containment (traversal → 400); shared API auth; 10 MB web limit.
- **Web IDE features (real backend actions only)**: project open/browse, file tree,
  multi-tab editor with Save, AI chat (engine/model selectors, sessions, SSE-streamed
  answers), coding tasks via the canonical TEIP surface with event polling, live
  `/api/events` stream, diagnostics.
- Verified: `WebIdeWorkspaceApiTest` (assets served, open/browse/read/save/containment/
  auth); live headless product: index.html served, project opened, new file saved to
  disk, and a real OpenCode coding task driven through the TEIP surface created
  `web-marker.txt` with the requested content. Browser UI interaction itself needs a
  graphical browser (not verified on this host); the API layer underneath is
  fully exercised.
- Known limitation (documented): non-git projects cannot produce git diffs, so TEIP
  result `modifiedFiles` is empty for non-git workspaces (file effects are still real).

## 12. Stage 7 — Cross-client and family verification (S7)

- **`CrossClientStateTest`**: one backend, two clients — a task submitted through the
  TEIP/Web surface is immediately visible in the RCP core services and diagnostics;
  a runtime instance added over HTTP is immediately present in the RCP registry;
  execution events are shared through the same store/event stream. No duplicate
  tasks, sessions or registries exist (the architecture has exactly one of each).
- **TEIP compatibility**: full canonical endpoint tests remain green
  (`TeipApiEndpointTest` 14, live `TeipOpenCodeEndToEndTest`) — EVO/TRYST wire
  contracts unchanged.
- Family note: EVO/TRYST live family tests (`DStudioLiveE2eTest`) run in their own
  repositories against a running DStudio at 48080; the DStudio side they exercise is
  covered here by the same TEIP surface tests.

## 13. Final verification totals (this report)

- `mvn -o clean verify`: **158/158 tests pass**.
- Live: headless product — Web IDE assets + workspace API + real OpenCode task through
  the TEIP surface; OpenAI-compatible adapter against real Ollama `/v1`; direct
  inference sessions (streaming, multi-turn, cancellation); SSE + reconnection;
  port-conflict guidance and clean termination; multi-instance engine administration.
