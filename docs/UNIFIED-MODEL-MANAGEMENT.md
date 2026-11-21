# DStudio — Unified Model Management Integration (shared ENR infrastructure)

Date: 2026-11-21
Status: Implemented, tested and live-verified (DStudio 2.6.11).

## 1. Objective

DStudio consumes the shared family model management service owned by ENR —
it holds **no** model management state, no download jobs and no provider
registry of its own. Pulls, progress, cancellation and removal execute in
ENR; DStudio serves them through its embedded backend and both UIs.

## 2. Components

| Component | Location | Role |
|---|---|---|
| `ModelManagementHttpHandler` | `eu.kalafatic.dstudio.teip.server` (path `/api/models`) | Passthrough to ENR `/api/v1/models/*`. ENR endpoint: configured ENR inference engine (`endpoint`), `dstudio.enr.endpoint` system property, default `http://localhost:28780`. Upstream failures → `UPSTREAM_UNAVAILABLE` (503) |
| `DStudioServer` registration | `eu.kalafatic.dstudio.teip.server` | Serves the handler whenever the inference registry exists (desktop and headless alike) |
| `ModelManagementClient` | `eu.kalafatic.dstudio.application.models` | RCP-side client over the local backend; plain DTO records (no Jackson types cross bundles) |
| Web Model Manager panel | `teip/web/index.html` + `app.js` | Provider select, model input, Pull, installed list, live downloads (1 s polling), Cancel/Remove |
| RCP `ModelManagerView` | `eu.kalafatic.dstudio.ui.inference` (id `eu.kalafatic.dstudio.ui.view.modelmanager`, Harness perspective) | Same operations; single-thread executor + `Display.asyncExec`, timer polling cancelled on dispose |

## 3. Compatibility note

The backend keeps serving the inference-engine surface (`/api/inference`)
unchanged; model management is a separate surface. Authentication follows
the same bearer-token/dev-mode rules as every other `/api/*` route.

## 4. Verification

- `eu.kalafatic.dstudio.tests.ModelManagementHttpApiTest` — 8 hermetic cases
  against a mock ENR. Full suite: 210 tests, 0 failures.
- Live headless product: `/api/models/providers` (ollama reachable through
  ENR), installed models (23 real Ollama models), pull → `COMPLETED`
  ("already installed"), live pull `DOWNLOADING 19%` → `CANCELLED`, jobs list
  shared with ENR. See ENR `ENR-MODEL-MANAGEMENT.md` for the family view.
