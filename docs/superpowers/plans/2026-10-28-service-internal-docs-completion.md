# Service Internal Documentation Completion Plan

> **For agentic workers:** Execute inline in this session. Documentation-only; do not change service behavior or infer unresolved API/schema decisions.

**Goal:** Keep each service's runtime API/schema reference complete and separately document the Project-first Kỳ 1 API and database design to the level already agreed, while marking unapproved contract details explicitly.

**Architecture:** Shared architecture decisions remain authoritative for the Project-first target. Service API/database docs separate current controller/JPA behavior from target contracts and schemas. Target tables describe agreed ownership, entities, relationships and constraints; proposed implementation details remain labeled as proposals/open. `shared` documents reusable Maven modules; `gateway` documents configured routes and its security/runtime boundary.

**Tech Stack:** Markdown, Spring Boot/WebFlux, Spring Data JPA, RabbitMQ, PostgreSQL, Maven.

**Spec:** `docs/project/architecture-decisions.md`, `docs/api/kanban-api.md`, and the existing Java/YAML source in each module.

## Global Constraints

- Do not edit application code, dependencies, or runtime configuration.
- Do not describe controller mappings as working endpoints when required application-port implementations are absent.
- Capture API method, path, headers, path/query parameters, request fields/validation, response shape/status, errors, and authentication behavior from source.
- Separate current JPA mappings from conceptual Project-first target schema. Do not invent exact fields, constraints, error payloads, or transitions where decisions remain open.
- Explain that each service stores shared messaging entities in its own database; `shared` is code, not a shared database or deployable service.
- Gateway docs must describe only routes/configuration present in `gateway/src/main/resources/application.yml`; note that gateway JWT authorization is not implemented.

## Files

- Update API/database docs for `user-service`, `board-service`, `task-service`, and `notification-service` (eight files under `services/*/docs/`).
- Add `shared/README.md`.
- Add `gateway/README.md` and `gateway/docs/routing.md`.
- Update `docs/README.md` to link the internal references and clarify precedence.

## Tasks

### Initial pass: runtime inventory and internal-module references

This pass documented current controllers/JPA mappings and the new shared/Gateway guides. It did **not** close the full Project-first target API payloads or per-service target schema; that gap was found later and is handled by the follow-up section below.

- [x] Document each declared API endpoint from its controller and DTO, including runtime readiness, auth/header behavior, validation, response shape/status, and implemented error mapping.
- [x] Document each current entity/table, columns and JPA constraints, relationships, indexes, defaults, repositories/query ordering, database name/port, and schema-management mode.
- [x] Add a target ownership/ERD section where approved; keep missing Project/Membership/Invitation schema and other unresolved details clearly marked as conceptual/open.
- [x] Describe shared Outbox/Inbox mappings within each service database and link their implementation guide.

### Task 2: Explain shared modules

- [x] Add `shared/README.md` with the purpose/dependency direction of `event-contracts` and `messaging-support`, event envelope/fields, Outbox/Inbox transaction boundaries, config flags, local build/use commands, and current no-consumer/no-queue-binding limitations.

### Task 3: Document Gateway internally

- [x] Add `gateway/README.md` with responsibilities, framework, port, dependencies, local run command, no-database status, and security/runtime limitations.
- [x] Add `gateway/docs/routing.md` with route IDs, path predicates, upstreams, ordering constraints, WebSocket declaration caveat, and Project-first routes not yet configured.

### Initial pass: link and runtime/target framing checks

- [x] Add links to the new internal docs in `docs/README.md`.
- [x] Check links to the new internal docs and distinguish runtime mappings from target design in the initial pass; this was a scoped check, not a complete API/schema audit.
- [x] Run the initial whitespace/link checks and one scoped independent documentation scan. The scan scope was too narrow to establish that all target API/schema gaps were closed; this limitation is the reason for the follow-up below.
- [x] Do not run application tests for this documentation-only task.

### Follow-up: Clarify skeleton code versus Kỳ 1 target

- [x] In service database docs, label `@Deprecated` entities as overlapping starter/skeleton source mappings only; do not imply persisted legacy data or a migration requirement.
- [x] Put the agreed Kỳ 1 data ownership/model summary before code-skeleton mapping inventory in each service database doc; keep unapproved schema details open.
- [x] State that Redis Authentication/Authorization Snapshot remains in the Kỳ 1 target; only its schema, TTL, projection/update path, and failure contract remain open.
- [x] Distinguish the `shared/` Java messaging library from the shared Redis runtime cache, and clarify that Gateway does not own the authorization snapshot.
- [x] Keep the confirmed database boundary explicit: Project and Board share `board_db`; the other services own their databases.
- [x] Recheck affected docs, links, UTF-8/LF/trailing whitespace, and run an independent scan.

> The tasks above completed the runtime inventory and internal-module guides. They did not complete the target API payload/response contracts or per-service target database design; those are added below.

### Follow-up: Complete the Project-first API and database design

- [x] Record the confirmed Member removal sequence consistently: revoke Project access in board-service first; publish durable cleanup work; task-service unassigns Cards asynchronously; preserve Comment/history. Explicitly identify the still-open mechanism needed to make Redis-backed authorization revocation effective before another request is admitted, including fail-closed behavior if the fence cannot be confirmed.
- [x] Expand `docs/api/kanban-api.md` into the canonical Kỳ 1 target contract: auth context, request/query fields, response shapes/status, role rules, business errors, invitation lifecycle, async account/project deletion, cursor pagination and reload behavior. Keep exact paths/payloads/errors marked proposed where not yet approved.
- [x] Add a corresponding target API section to each service API document, including which routes are public through Gateway, internal/event-driven only, or runtime-only; document the deletion-operation GET referenced by `statusUrl`.
- [x] Add a per-service target schema section for `user_db`, `board_db`, `task_db`, and `notification_db`: entities/columns, PKs, logical vs physical references, agreed uniqueness/status/order constraints, required indexes for proposed queries, and Outbox/Inbox locality. Distinguish confirmed rules from schema proposals and unresolved fields.
- [x] Reconcile architecture decisions, account-deletion guide, service task sheets, integration checklist, docs index and README with the target contracts; include Owner's proposed pending-Invitation list route and its query index.
- [x] Read and cross-check the updated target contract/schema sections; verify cross-document values/relative links and UTF-8/LF/trailing whitespace, run `git diff --check`, and obtain a fresh independent documentation scan. Do not run application tests for this documentation-only follow-up.
- [x] Clarify the per-Column infinite-scroll wire proposal: `columnId` is required for that route shape, and opaque cursors are bound to Board, Column and sort. Keep it labeled as proposal pending approval; `(position,id)` remains a total ordering without the Column filter.
