# Common Documentation Alignment Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make shared Dev-Platform documentation consistently describe the approved Project-first Kỳ 1 target while clearly labeling legacy backend documentation and unresolved contracts.

**Architecture:** Keep `docs/project/architecture-decisions.md` as the source for agreed backend decisions. Update the shared README, API overview, integration checklist, and docs index; preserve service-specific documents for a later pass and label the existing Kanban demo spec as a mock/UI reference whose backend topology has been superseded.

**Tech Stack:** Markdown documentation; no runtime code changes.

**Spec:** `docs/project/architecture-decisions.md`, plus the user's instruction to update shared documentation before service-specific documents.

## Global Constraints

- Kỳ 1 has Gateway plus `user-service`, `board-service`, `task-service`, and `notification-service`; Project/Membership belong in `board-service` and `board_db`.
- API Gateway is the target authentication boundary; internal REST authorization lookup remains deferred until internal service communication is secured.
- Invitation creates a `PENDING` invitation; only acceptance creates Membership.
- Keep unresolved payloads, error behavior, retry/DLQ, snapshot schema, and integration barriers explicitly open.
- Do not edit `services/*/docs`, `docs/project/01-*` through `05-*`, or UI-specific web-demo documents in this shared-doc pass.
- Do not change runtime code or claim the target architecture is implemented.

## Review Focus

- Readers must be able to distinguish approved backend target from current Board-first runtime and from the MSW demo.
- No old Kỳ 2 `Board → Issues` / `Task → PR` mapping may remain presented as the current decision.
- Shared docs must not imply that Project/Membership APIs, gateway authentication, or cascade workers are already implemented.
- Links between the docs index, architecture decisions, API document, and integration checklist must resolve.
- Service-specific docs should be clearly identified as the next alignment phase, not silently treated as current target contracts.

---

### Task 1: Establish Shared Documentation Authority

**Files:**
- Create: `docs/README.md`
- Modify: `docs/project/architecture-decisions.md`
- Modify: `README.md`

- [x] Add the shared-doc index and explain which documents describe decisions, API target, integration, current implementation, and mock references.
- [x] Update README with Project-first Kỳ 1, service boundaries, actual implementation caveat, and non-final Kỳ 2 scope.
- [x] Replace the architecture note's stale-doc list with the explicit documentation authority and staged-alignment policy.

### Task 2: Align the Common API and Integration Documents

**Files:**
- Modify: `docs/api/kanban-api.md`
- Modify: `docs/project/06-integration.md`
- Modify: `docs/superpowers/specs/2026-10-21-kanban-web-demo-design.md`

- [x] Put the Project-first API route map and unresolved contract markers before the legacy Board-first reference.
- [x] Rewrite the shared integration checklist around Project ownership, Invitation acceptance, member work, and account-deletion completion gates.
- [x] Clarify that the approved React/MSW demo spec remains a UI/mock reference, while its backend topology and permission scope do not supersede current architecture decisions.

### Task 3: Verify Documentation Consistency

**Files:** all files above.

- [x] Search shared docs for stale claims and verify every remaining old API/Kỳ 2 claim is explicitly labeled legacy or historical.
- [x] Verify local Markdown links, UTF-8 content, trailing newline, and `git diff --check`.
- [x] Do not run application tests; this change only edits documentation.
