# Service Documentation Alignment Plan

> **Historical execution note (2026-10-29):** This plan records an earlier service-doc alignment pass. Current System Admin, Project-creation, and Account-revocation policy is maintained in [architecture decisions](../../project/architecture-decisions.md) and [Kanban API](../../api/kanban-api.md); the constraints below describe that earlier pass.

> **For agentic workers:** Execute inline in this session. This is a documentation-only alignment plan; do not implement backend behavior.

**Goal:** Align service-specific and project-wide documentation with the approved Project-first Kỳ 1 decisions while clearly separating target contracts from the current Board-first runtime.

**Architecture:** `docs/project/architecture-decisions.md` remains the authority for agreed architecture. `docs/api/kanban-api.md` describes target routes and unresolved contract details; service docs describe actual code/schema and link back to the target. Historical plans and demo-only specifications remain historical references.

**Tech Stack:** Markdown, Spring Boot/Java source as evidence, PowerShell/rg for documentation checks.

**Spec:** `docs/project/architecture-decisions.md`, `docs/api/kanban-api.md`, and existing `docs/project/account-deletion-skeleton-guide.md`.

## Global Constraints

- Kỳ 1 has four services: `user-service`, `board-service`, `task-service`, and `notification-service`; Project/Membership/Invitation/Board/Column belong to `board-service` and `board_db`.
- Distinguish agreed target behavior from implemented runtime behavior in every document that discusses APIs, schema, or service ownership.
- Do not invent routes, payloads, schema fields, event semantics, or retry/security policies for decisions still marked open.
- Preserve historical plan content and demo-only scope; correct their index/status labels instead of rewriting history.
- Do not change application code or run application tests in this documentation task.

## Review Focus

- An old Board-first endpoint or owner/key model could be mistaken for the new target contract; label it as current runtime/reference.
- An unimplemented scaffold could be mistaken for a working cascade flow; name the concrete missing listener, worker, persistence adapter, API, or wiring.
- An unresolved design item could accidentally become an implied decision while synchronizing service docs; link it to the architecture decision register.
- Internal REST, RabbitMQ, and Redis could be described as active secure integrations when only boundaries or infrastructure exist.
- Links among shared docs, service docs, plans, and demo docs could break during the move to a synchronized document hierarchy.

---

### Task 1: Align service API and database references

**Files:**
- Modify: `services/user-service/docs/api.md`, `services/user-service/docs/database.md`
- Modify: `services/board-service/docs/api.md`, `services/board-service/docs/database.md`
- Modify: `services/task-service/docs/api.md`, `services/task-service/docs/database.md`
- Modify: `services/notification-service/docs/api.md`, `services/notification-service/docs/database.md`

- [x] Document current controllers and entities from source, then state the agreed Project-first target and link to the shared API/decision register.
- [x] Keep absent Project/Membership/Invitation models, Admin routes, listeners, workers, and API contracts explicitly unimplemented or open.
- [x] Record exact deletion scaffold fields and boundaries without implying the scaffold is wired end-to-end.

### Task 2: Align service task sheets and integration checklist

**Files:**
- Modify: `docs/project/01-user-service.md` through `docs/project/05-gateway.md`
- Modify: `docs/project/06-integration.md`
- Modify: `docs/project/account-deletion-skeleton-guide.md`

- [x] Reframe each sheet around Project-first ownership and APIs while retaining implementation TODOs grounded in actual code.
- [x] Remove stale wording that treats Project service as a separate deployable service or Board as the authorization boundary.
- [x] Mark documentation alignment as complete in the integration checklist, without marking runtime integration criteria complete.

### Task 3: Synchronize common docs and client references

**Files:**
- Modify: `README.md`, `docs/README.md`, `docs/project/architecture-decisions.md`, `docs/api/kanban-api.md`
- Modify: `web-demo/README.md`, `mobile/README.md`
- Keep historical: `docs/superpowers/plans/*`, `docs/superpowers/specs/*`, `web-demo/docs/superpowers/*`

- [x] Ensure the README and index explain the single-source decision hierarchy and current code status.
- [x] Register any newly noticed unresolved contract item instead of assigning it to a service without a decision.
- [x] Keep demo/mobile docs explicit about mock or integration status and link to the target contract.
- [x] Preserve historical plans/specs; update only status notes or indexes if needed.

### Task 4: Documentation consistency verification

**Files:** all Markdown files changed in Tasks 1–3.

- [x] Check relative Markdown links, UTF-8 readability, LF at EOF, and trailing whitespace.
- [x] Search active docs for stale Board-first claims, old service count/topology, contradictory membership flows, and claims that cascade/messaging is end-to-end.
- [x] Run `git diff --check`; do not run application tests.
- [x] Request one fresh self-review scan of the changed docs, then resolve material findings.
