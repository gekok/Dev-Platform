# Project Creation and System Role Documentation Plan

> **For agentic workers:** Execute inline in this session. Documentation-only; preserve runtime behavior and keep unresolved role mechanics explicitly open.

**Goal:** Align Dev-Platform documents on who may create a Project, how its Owner is established, how Gateway identity is trusted, and what remains undecided about System Admin role management.

**Architecture:** `docs/project/architecture-decisions.md` remains the source of agreed decisions. Sync the common API, user/board service guides, Gateway documentation, and document index to it. Keep runtime status separate from the target contract.

**Tech Stack:** Markdown documentation; no runtime code changes or application tests.

**Spec:** User-approved direction in this conversation, `docs/project/architecture-decisions.md`, and `docs/api/kanban-api.md`.

## Global Constraints

- Only an active business Account with identity authenticated by Gateway may create a Project; the creator becomes the single Owner.
- Derive the actor from trusted Gateway identity; do not accept a caller-supplied actor ID.
- `SYSTEM_ADMIN` grants account-administration authority only; it does not itself grant Project authority.
- Keep Account dual-role policy, System Admin bootstrap/provisioning, and final role-storage schema open.
- Do not imply Gateway authentication or Project-first endpoints are implemented in runtime.
- Preserve unrelated existing documentation edits; do not change application code.

## Review Focus

- Anonymous or inactive Accounts are not described as eligible to create Projects.
- A System Admin-only identity is not given Project rights; dual-role behavior remains explicitly open.
- Project and Owner membership are created atomically from Gateway-verified identity.
- Only an Account verified as `ACTIVE` may create a Project. Kỳ 1 has no Account deletion transition; authorization still fails closed when the Account snapshot cannot be verified.
- All docs distinguish the target contract from the current `X-User-Id` stub and incomplete Gateway.
- Existing System Admin role storage/provisioning open items are narrowed, not silently marked implemented.

---

### Task 1: Update the authoritative decision and common API

**Files:**
- Modify: `docs/project/architecture-decisions.md`
- Modify: `docs/api/kanban-api.md`
- Modify: `README.md`

- [x] Record the approved Project creation identity/Owner rule and retain unresolved role bootstrap and dual-role choices.
- [x] Align shared Project API/auth wording and high-level README summary.

### Task 2: Align service and Gateway documentation

**Files:**
- Modify: `services/user-service/docs/api.md`
- Modify: `services/user-service/docs/database.md`
- Modify: `services/board-service/docs/api.md`
- Modify: `services/board-service/docs/database.md`
- Modify: `docs/project/01-user-service.md`
- Modify: `docs/project/02-board-service.md`
- Modify: `docs/project/05-gateway.md`
- Modify: `docs/project/06-integration.md`
- Modify: `gateway/README.md`
- Modify: `gateway/docs/routing.md`

- [x] Explain the candidate System Admin storage separately from its unresolved bootstrap/grant/revoke policy.
- [x] State the authenticated active Account requirement and atomic Project/Owner creation in board-service docs.
- [x] Record the Project authorization rule in board_db docs without presenting it as a database constraint or implemented runtime behavior.
- [x] State Gateway identity validation/header sanitization as target behavior while documenting current runtime gaps.

### Task 3: Update the docs index and verify consistency

**Files:**
- Modify: `docs/README.md`
- Review: all files above.

- [x] Replace stale “role storage” wording with the remaining precise decisions.
- [x] Check changed text, cross-document consistency, local links, UTF-8/LF, trailing whitespace, and `git diff --check`.
- [x] Do not run application tests for this documentation-only change.

### Task 4: Bring every related demo and cascade-plan document up to date

**Files:**
- Modify: `docs/project/03-task-service.md`
- Modify: `docs/project/account-deletion-skeleton-guide.md`
- Modify: `services/task-service/docs/api.md`
- Modify: `docs/superpowers/specs/2026-10-21-kanban-web-demo-design.md`
- Modify: `docs/superpowers/plans/2026-10-21-kanban-web-demo.md`
- Modify: `docs/superpowers/plans/2026-10-28-account-deletion-cascade-skeleton.md`
- Modify: `docs/superpowers/plans/2026-10-28-common-docs-alignment.md`
- Modify: `docs/superpowers/plans/2026-10-28-service-docs-alignment.md`
- Modify: `docs/superpowers/plans/2026-10-28-service-internal-docs-completion.md`
- Modify: `web-demo/README.md`
- Modify: `web-demo/docs/superpowers/specs/2026-10-21-project-board-navigation-design.md`
- Modify: `web-demo/docs/superpowers/plans/2026-10-21-project-board-navigation.md`

- [x] Add the current backend authorization policy and links to all matching docs; preserve mock-only, historical-plan, and scaffold-only scope.
- [x] Search all Markdown matches again and verify each is aligned or explicitly historical/demo-only.
