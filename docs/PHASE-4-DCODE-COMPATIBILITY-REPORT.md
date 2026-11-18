# PHASE-4-DCODE-COMPATIBILITY-REPORT.md

**DStudio Phase 4 — WP-4-FINAL.5 deliverable (read-only inspection)**
**Date:** 2026-11-16
**Status:** DCode runtime integration remains **DEFERRED to the parallel Phase 5 work** (Product
Owner decision). This report verifies contract compatibility as far as the current Phase 5
state permits. No DCode files were modified.

## 1. DCode Current State (verified by inspection)

- Repository `/home/petr/git/DCoder-121126`: Phase 5 actively in progress. 7 bundles
  (`config`, `core`, `harness`, `java.tooling`, `persistence`, `repositories`, `server`),
  Tycho build, tests, and WP reports (`PHASE-5-ARCHITECTURE-BASELINE.md`,
  `PHASE-5-WP-5.6-JDT-IMPLEMENTATION-REPORT.md`, `PHASE-5-WP-5.9-API-SSE-AUDIT.md`).
- DCode owns a NanoHTTPD HTTP server (`/api/v1`) with Bearer auth **only when a token is
  configured** (explicit opt-out, mirroring TEIP 1.1 §6 conventions) and a TEIP-shaped
  top-level error envelope.
- `ExecutionState` = `QUEUED | RUNNING | WAITING | COMPLETED | FAILED | CANCELLED` — the
  **exact canonical TEIP 1.1 external state set** (§4.4).
- **No TEIP client and no DStudio connector exist yet** — DCode currently has no
  DStudio-facing code (expected: family integration is its WP5.12).
- DCode `AGENTS.md`: family integration is wire-only (TEIP 1.1 / FHC), DTOs owned per
  product, no product-to-product dependencies — consistent with the Phase 4 architecture.

## 2. DStudio Contract (what DCode connects to — implemented and live-tested)

DStudio exposes the canonical TEIP 1.1 provider endpoint set at `http://localhost:48080`
(`GET /teip/v1/health`, `GET /teip/v1/capabilities`, `POST /teip/v1/executions` (202/200 +
persisted idempotency), `GET .../status`, `GET .../events` (`afterSequence`/`nextSequence`
cursor), `GET .../result` (409 until terminal), `POST .../cancel`; canonical error
envelope/codes; mandatory Bearer or explicit `dstudio.teip.devMode` loopback opt-out).
DStudio-specific request fields are confined to `parameters.*` (open-world):
`parameters.workingDirectory` (required), `parameters.harness` (`opencode` | `aider`),
`parameters.timeoutSeconds`. The provider is proven live by EVO and TRYST connectors
(`DStudioLiveE2eTest` in both products, real OpenCode/Aider executions, 2026-11-16).

## 3. Compatibility Verification Matrix (DCode ↔ DStudio)

| Requirement | DStudio provides | DCode state | Verdict |
|---|---|---|---|
| TEIP 1.1 wire compatibility | ✅ full provider | no client yet | 📄 contract sufficient; client to be implemented |
| Client identity (`caller.system=DCODE`) | ✅ accepted uniformly (any system value) | no caller yet | ✅ no DStudio change needed |
| Harness discovery (`/capabilities`) | ✅ | not consumed yet | ✅ contract sufficient |
| Request structure (canonical fields + `parameters.*`) | ✅ | not implemented yet | ✅ contract sufficient |
| `parameters.workingDirectory` | ✅ implemented | DCode has sandbox/workspace concept | ✅ compatible |
| `parameters.harness` | ✅ (`opencode`, `aider`, structured 422 on unknown) | DCode harness SPI exists (native agent) | ✅ compatible (DCode may use native agent locally; DStudio path is optional) |
| Session/task identity | ✅ opaque ids + `correlationId` | session-centric ids | ✅ compatible (opaque strings) |
| Streaming/event correlation | ✅ `EXECUTION_*` events, persisted cursor | DCode SSE lacks monotonic ids (own server, WP5.9 gap) | ⚠️ DCode client must implement cursor polling; DStudio side is standard |
| Cancellation | ✅ idempotent | — | ✅ contract sufficient |
| Result + changed files | ✅ `modifiedFiles`, `patch`, metrics | — | ✅ contract sufficient |
| Error mapping | ✅ canonical envelope/codes | DCode server already uses TEIP error shape | ✅ compatible |
| Auth | ✅ Bearer / explicit dev-mode opt-out | same explicit opt-out model on DCode server | ✅ compatible |

## 4. Missing Implementation (DCode side)

1. A TEIP 1.1 client (reference implementations: EVO `DStudioTeipClient`, TRYST `TeipClient`).
2. A `DStudioCodingHarnessAdapter` equivalent registered in DCode's harness SPI, with
   `caller.system = "DCODE"`.
3. Event-cursor handling (`afterSequence`/`nextSequence`) in the client polling loop.
4. Timeout/cancellation mapping to DCode's task lifecycle (DCode stays sovereign).

## 5. Required Changes on the DStudio Side

**None identified.** The provider contract is generic over callers and proven by two
independent client implementations. If DCode needs a model for its native agent advertised
via `availableModels`, DStudio already aggregates per-harness model descriptors.

## 6. Sufficiency of the Current DStudio Contract

**Sufficient.** The same contract satisfied EVO and TRYST live end-to-end. Nothing in
DCode's verified state (execution-state set, error model, auth conventions, sandbox
concept) conflicts with it. One documented nuance: the Phase 5 baseline document
(`PHASE-5-ARCHITECTURE-BASELINE.md`) was written before the Phase 4 DStudio implementation
existed and states "DStudio ... Phase 4 never implemented" — that statement is now stale;
DStudio Phase 4 IS implemented and live-tested. The Phase 5 WP5.12 planning should treat
DStudio as an available TEIP provider rather than deferring DStudio roles to TEIP 2.0.

## 7. Exact Next Integration Steps (when Phase 5 reaches WP5.12)

1. DCode implements a TEIP 1.1 client (JDK HttpClient; no new dependencies).
2. DCode registers a `DStudio` harness adapter (type `DSTUDIO`) in its existing harness
   registry, endpoint default `http://localhost:48080`, config keys endpoint/authToken/
   harness/model/timeoutSeconds (persisted per DCode conventions; token never persisted).
3. Wire the adapter into DCode's task/execution orchestration exactly like TRYST's
   `HarnessAiDelegator` seam.
4. Run the shared live test template: start DStudio headless (`./dstudio -headless`),
   then `DCode → POST /teip/v1/executions {caller.system:"DCODE", parameters.workingDirectory,
   parameters.harness:"opencode"|"aider"}` → poll status/events → assert real file changes
   in DCode's sandbox and `modifiedFiles` in the result.
5. No DStudio, EVO or TRYST changes required.

## 8. Classification

- **DCode runtime E2E: DEFERRED** to parallel Phase 5 (no connector exists yet; nothing to
  test against DCode).
- **Contract compatibility: PASS** (verified against DCode's actual Phase 5 code/docs;
  no conflicts; DStudio contract sufficient).
- **DStudio-side changes required: none.**
