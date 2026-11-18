# PHASE-8 — DCode & DStudio Coordinated Implementation Report

**Date:** 2026-11-20
**Scope:** EVO Family brief — DCode (lightweight AI coding harness) and DStudio
(comprehensive Eclipse multi-harness AI IDE), developed together as one coordinated
effort. All statements below describe implemented and verified behavior only;
PASS / PARTIAL / FAIL / DEFERRED are explicit.

---

## 1. Joint Architecture Audit

| Component | Finding | Evidence |
|---|---|---|
| DCoder-121126 | **Empty skeleton** at audit start (10 plugin dirs, 0 sources, no pom) — bootstrapped during this phase | `find` = 0 java files |
| DStudio-121126 | Full platform: 7 bundles, 160/160 tests, TEIP 1.1 provider, SSE, 4 harnesses, 4 runtime types, Web IDE, RCP | build + tests |
| ENR | Real runtime (evo-native-runtime-161126, 96 sources; wire contract `/health`, `/enr/v1/models`, `/enr/v1/chat`, `/enr/v1/runtime`, load/unload) | `enr-java-api/EnrClient` |
| Shared contracts | TEIP 1.1 (canonical) for DStudio; FHC 1.0 wire for DStudio→DCode; ENR 1.0 wire for inference | DStudio adapters |

**Duplication risks resolved:** one DCode session manager (in DCode); DStudio observes
it through the harness contract only (§10: no second DCode session manager in DStudio).
No circular dependency: DCode never imports DStudio.

## 2. DCode — implemented (bootstrapped from skeleton)

- **8 bundles**: core (domain + SPI + event bus), persistence (SQLite V1,
  sessions/messages/workspaces/repositories/changes), enr (direct ENR provider client),
  harness (native deterministic tool-executing engine), server (headless, FHC 1.0),
  client (HTTP client), ui (lightweight RCP), feature/product/tests.
- **Native engine**: real file effects (write_file tool parsing, containment, real
  unified diffs); optional ENR planning (never required for correctness).
- **FHC 1.0 server** (port **58080**, family 58xxx convention): `/health`,
  `/api/v1/workspaces`, `/api/v1/workspaces/{id}/repositories`, `/api/v1/sessions`
  (create/start/cancel/state/messages/changes/list), `/api/v1/providers`,
  `/api/v1/models`, `/api/status`, **SSE** `/api/events`; loopback-open + optional
  bearer token; SQLite persistence; session history.
- **Headless application** (`eu.kalafatic.dcode.server.application`) — no SWT.
- **RCP**: lightweight client (chat + sessions + workspace picker), embedded shared
  backend. Compile-verified; GUI runtime requires a graphical host — PARTIAL.

## 3. DStudio — coordinated changes

- `DCodeFamilyIntegrationTest` (live): DStudio executes through DCode, collects
  changedFiles + diff, asserts real file effects. **LIVE PASS**.
- DCode remains DStudio's default harness (adapter unchanged; endpoint 58080 now a
  real server).

## 4. ENR integration status

- DCode: direct ENR provider (health/models/chat), registered as default provider.
  **Live status: ENR runtime not reachable on this host → available=false reported
  honestly; live inference DEFERRED until an ENR instance runs.**
- DStudio: ENR adapter (existing, wire-tested; same limitation).
- Both consume the same ENR 1.0 wire — no duplicated ENR implementations.

## 5–6. Compatibility matrices

| Harness | DCode | OpenCode | Aider | Claude Code |
|---|---|---|---|---|
| Providers | DCODE_NATIVE (ENR via DCode) | OLLAMA, OPENAI_COMPATIBLE | OLLAMA, OPENAI_COMPATIBLE, ANTHROPIC | ANTHROPIC |
| Live-verified | **YES (DStudio→DCode)** | YES | YES | NO (CLI absent — test skips) |

| Runtime | ENR | Ollama | OpenAI-compatible | Anthropic |
|---|---|---|---|---|
| DCode | wire; live DEFERRED (ENR down) | via OPENAI_COMPATIBLE — | — | — |
| DStudio | wire | **LIVE** | **LIVE (Ollama /v1)** | wire; live DEFERRED (no key) |

## 7–8. Verification (actual results)

| Test | Result |
|---|---|
| DCode build `mvn -o clean verify` | PASS — 7/7 (EngineTest real file effects incl. containment, ServerWireTest full FHC flow, EnrProviderTest graceful degradation) |
| DStudio build `mvn -o clean verify` | PASS — 160/160 |
| DCode headless live | PASS — 58080, health UP, providers reported, SSE SessionEvents streamed, SIGTERM clean shutdown, port released |
| **Family E2E (DStudio→DCode)** | **PASS live** — task → DCode session → real `family-marker.txt` (FAMILY_OK) → changedFiles + diff collected in DStudio |
| DCode independent of DStudio | PASS — runs standalone; no DStudio bundles in its product |
| Cross-client state | PASS (DStudio `CrossClientStateTest`; DCode SSE + REST state identical across clients) |
| RCP runtime (both) | PARTIAL — compile-verified only; graphical host required (none on this build host) |
| DCode Web IDE | DEFERRED — DCode web frontend is the next workstream (DStudio Web IDE exists and is live-verified) |
| ENR live inference (both) | DEFERRED — no reachable ENR runtime on this host; wire contracts tested |
| Multi-harness workflows (§3.2 orchestration: generate→review chains) | DEFERRED — explicit workflow orchestration not yet implemented |

## 9. Remaining limitations (honest)

1. DCode Web IDE not built (DEFERRED).
2. DCode RCP + DStudio RCP GUI runtime verification pending a graphical host.
3. ENR live inference pending a running ENR instance.
4. Multi-harness chained workflows (one harness reviews another's output) not yet
   orchestrated in DStudio (§3.2).
5. DCode engine is deterministic/tool-based; full LLM-driven agent behavior comes with
   ENR live availability (planning already wired, execution tools are native).

## 10. Contract summary (shared, do not break)

- **TEIP 1.1** (DStudio provider) — unchanged, all canonical tests green.
- **FHC 1.0** (DStudio→DCode): as implemented in DCode `DCodeHttpHandler` and consumed
  by DStudio `DCodeHarnessAdapter`.
- **ENR 1.0** (`/enr/v1/*`) — consumed by both products, implemented once per product
  as a client.
- **Ports**: EVO 28xxx / TRYST 38xxx / DStudio 48xxx / DCode 58xxx (+100 diagnostics).
