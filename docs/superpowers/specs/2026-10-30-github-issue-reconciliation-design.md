# GitHub Issue Reconciliation Design

## Intent

Bring the GitHub backlog into line with the approved Project-first Kỳ 1 design and the code/docs that now exist. Add a repeatable contributor guide for writing issues and recording acceptance evidence.

## Goals

- Keep all existing issue numbers, parent/child links, open/closed state, assignees, and project-board status values.
- Rewrite issues #1–35 so scope and acceptance criteria reference the current service ownership, API target, Redis authorization snapshot, event, and integration decisions.
- Keep mobile issues #36–41, distinguish Kỳ 1 core flows from extras whose phase is not approved, and avoid silently declaring those extras Kỳ 2.
- Add repository guidance and GitHub issue forms for implementation tasks, bugs, and unresolved design decisions.

## Non-goals

- Do not close issues, change project-board statuses, reassign people, or create replacement issues during reconciliation.
- Do not mark a feature complete just because its docs or issue text changed.
- Do not turn open architecture proposals into approved contracts.

## Source of truth

Use `docs/project/architecture-decisions.md` for agreed/open decisions, `docs/api/kanban-api.md` for the target HTTP contract, service task sheets for ownership and implementation scope, and source/docs runtime sections for what is actually implemented. Demo specs are UX/mock references, not backend authority.

## Issue and acceptance policy

Every issue states its phase, owning service, outcome, scope, dependencies/open decisions, observable acceptance criteria, and the evidence expected at acceptance. Keep issues open until each criterion is verified and the change is merged with evidence linked. When a decision blocks implementation, record the exact decision and keep it in `BackLog`; do not invent a default in the issue body.

## Implementation calls

- Preserve existing GitHub issue identities and board workflow; update issue content only.
- Preserve current project-board statuses because implementation progress cannot be inferred safely from issue text or source alone.
- Treat unapproved mobile add-ons as proposed scope while retaining approved-looking core Kanban flows for later delivery against the canonical API.
- Add concise issue forms and a linked Vietnamese guide so new work follows the same rules.
