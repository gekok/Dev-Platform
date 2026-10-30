# GitHub Issue Reconciliation Implementation Plan

> **For agentic workers:** Use the native inline workflow for this documentation and issue update. Steps use checkbox syntax for tracking.

**Goal:** Align the 41 open GitHub issues and repository issue guidance with the approved Project-first Kỳ 1 design without falsifying delivery progress.

**Architecture:** Preserve issue identity and board state. Rewrite issue scope/acceptance from the canonical architecture, API, and service task sheets; classify mobile add-ons as unapproved where the current phase is unclear. Add local templates and one guide linked from repository contributor/docs entry points.

**Tech Stack:** GitHub Issues/Project, Markdown, GitHub Issue Forms YAML.

**Spec:** `docs/superpowers/specs/2026-10-30-github-issue-reconciliation-design.md`

## Global Constraints

- Do not close issues, alter board status, change assignees, or create replacement issues.
- Do not present unresolved decisions or target designs as shipped/approved.
- Do not include Kỳ 2 Account deletion/cascade in Kỳ 1 acceptance criteria.
- Every implementation issue must have observable acceptance criteria and a place for verification evidence.
- Preserve the user's existing untracked files in the working tree.

## Review Focus

- Existing issue body refers to Board-first route while the target contract is Project-first; acceptance must cite the target API and distinguish current runtime.
- Redis is required by authorization snapshots but is absent from Compose; gateway/infrastructure acceptance must track it.
- Internal REST is not a trusted service channel yet; issues must not require it as an active Kỳ 1 integration.
- Account/Project deletion was deferred; old cascade skeleton work must not re-enter Kỳ 1 scope.
- Mobile issue extras have unclear phase; preserve them as proposals rather than silently approving or deleting them.

---

### Task 1: Add issue-writing and acceptance guidance

**Files:**
- Create `docs/project/github-issue-workflow.md`.
- Create `.github/ISSUE_TEMPLATE/implementation-task.yml`.
- Create `.github/ISSUE_TEMPLATE/bug-report.yml`.
- Create `.github/ISSUE_TEMPLATE/design-decision.yml`.
- Modify `AGENTS.md` and `docs/README.md` to link the guide.

- [x] Define issue readiness, required sections, evidence-based acceptance, and board status meanings.
- [x] Ensure templates require phase, scope/context, acceptance criteria, dependencies, and verification evidence as appropriate.
- [x] Verify relative links, YAML structure, and final newline/whitespace.

### Task 2: Reconcile backend and integration issues

**External GitHub issues:** #1–35.

- [x] Rewrite parent issues #1–#6 as Kỳ 1 service/integration scopes based on current docs.
- [x] Re-scope child issues #7–#35 so each describes one deliverable with dependencies and observable acceptance criteria.
- [x] Retain valid unfinished implementation work; identify decision-gated tasks without inventing decisions.
- [x] Remove old Board-first acceptance from target routes, remove `PR_REVIEWED` from Kỳ 1, and add Redis to local infrastructure acceptance.

### Task 3: Clarify mobile issue phase and scope

**External GitHub issues:** #36–41.

- [x] Keep core authentication, Project workspace, Card workflow, Notification, and API integration work tied to the canonical Kỳ 1 contract.
- [x] Mark Google OAuth, refresh-token policy, Category/Tag, Subtasks, advanced search, reminders, and statistics as proposals/phase-unapproved unless the current docs approve them.
- [x] Preserve issue and project-board status; do not claim implementation completion.

### Task 4: Verify local docs and remote issue updates

- [x] Read back all 41 updated issue bodies and confirm title/number/state were preserved; verify assignees, labels, and milestones unchanged.
- [x] Update issue title/body only; no Project status field was sent to GitHub.
- [x] Run `git diff --check`, inspect every modified doc/template, verify links and YAML structure, and check LF at EOF.
- [x] Record verification evidence and report remote/local changes separately.
