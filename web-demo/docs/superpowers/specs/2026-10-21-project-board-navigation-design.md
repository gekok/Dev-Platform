# Project and board navigation design

## Status

Approved in chat: compact avatar account menu, no Overview/board tabs, searchable Board switcher, an explicit per-user/per-Project default saved through the mock HTTP API, Kanban retained with a clearer workspace, and `+ New board` at the dropdown footer with no Manage boards entry. This technical specification is awaiting review before the implementation plan is written.

## Context

The demo currently has two project-level destinations: `/projects/:projectId` renders a Project Overview containing board cards, and `/projects/:projectId/boards/:boardId` renders the Kanban board. The project navigation shows Overview and every board as tabs. The global header also displays the account name, email, and sign-out action permanently. Board lists are already available through the mock API, while user preferences do not yet exist.

The current browser database is persisted under `dev-platform-demo:db:v1` with store schema version 1. Existing board creation is Owner-only, and board lists are returned in creation-time order.

## Goals

- Reduce persistent header space with an avatar-triggered account menu.
- Keep the two horizontal navigation levels: account/workspace navigation first, project identity second.
- Remove Overview and board tabs from project navigation.
- Open a user's explicitly selected default board when entering a Project.
- Let users search and switch boards from the current board header.
- Let users set a per-user, per-project default board through the mock HTTP API.
- Keep the Kanban interaction and make the board read as one clearly framed workspace.
- Preserve existing browser demo data when adding preference storage.

## User experience

### Account menu

- The global header shows the user's avatar as a compact button.
- Activating it opens a menu with the user's full name, email, and Sign out action.
- Sign out keeps its current behavior: clear the session and query cache, then navigate to `/login`.
- The project/workspace selector, notification control, and demo reset control remain separate header actions.
- The menu closes after selecting Sign out, pressing Escape, or clicking outside it.

### Project entry and navigation

- The second horizontal bar keeps the current Project mark, name, and membership role. It has no Overview or board tabs.
- The existing `/projects/:projectId` route becomes the Project entry point. It loads the Project, its Boards, and the current user's default-board preferences.
- If a valid default exists for that user and Project, the entry point navigates to that Board.
- If no valid default exists, it opens the first Board in the API's existing creation-time order. This fallback does not silently save a default.
- If the Project has no Boards, an Owner sees an empty state with a New Board action; a Member sees an explanation that the Owner must create one.
- Project cards and the global Project selector continue to navigate to `/projects/:projectId`, so both use the same resolution behavior.
- Direct links to `/projects/:projectId/boards/:boardId` continue to open that Board.
- Board-level back links display “All Projects” and point to `/projects`, because the Project route now resolves to a Board and must not link back to itself.

### Board selector and creation

- The current Board name in the Board header becomes a searchable dropdown. Search matches Board name and key.
- Choosing a Board navigates to its existing Board route.
- Each Board row has a separate Set as default action. Saving a default does not switch the current Board; the selected Board receives a default indicator after the preference is saved.
- On the saved-default row, Clear default replaces Set as default. Clearing does not switch the current Board; the next Project entry falls back to the first Board.
- A persistent `+ New board` action sits at the bottom of the dropdown. There is no Manage boards item or link.
- Only Project Owners see `+ New board`, matching the existing API permission. It opens an accessible creation dialog with the existing name/key fields and default columns. On success, the app navigates to the new Board. Creating a Board does not automatically change the saved default.
- For Projects with no Boards, the Owner's empty state opens the same creation dialog so the first Board is still reachable.

### Kanban workspace

- Keep the current Kanban columns, cards, movement controls, and horizontal scrolling behavior.
- Visually group the Board identity, column toolbar, and columns into one workspace with clear surface boundaries and consistent spacing. Preserve the higher-contrast palette already used by the demo.
- Keep the canvas usable at narrow widths: the global header and project identity may wrap or scroll as they do today, and Kanban columns remain horizontally scrollable.

## Preference API and data

All routes are under `/api/v1` and require the existing bearer-token authentication.

| Method and route | Behavior | Success response |
| --- | --- | --- |
| `GET /users/me/preferences/default-boards` | List saved defaults visible to the authenticated user | `{ "defaultBoards": [{ "projectId": 1, "boardId": 11 }] }` |
| `PUT /users/me/preferences/default-boards/:projectId` | Set or replace that Project's default Board from `{ "boardId": 11 }` | `{ "projectId": 1, "boardId": 11 }` |
| `DELETE /users/me/preferences/default-boards/:projectId` | Clear that Project's explicit default | `204 No Content` |

- The user ID always comes from the bearer token; clients cannot choose another user in the URL or request body.
- Persist preferences as rows keyed by `(userId, projectId)`, each containing `boardId`.
- A user must be a member of the Project to read or change its preference. A saved Board must belong to that Project. Invalid payloads return `400`, missing Projects return `404`, non-members return `403`, and missing/invalid authentication returns `401`.
- A Board ID that does not exist returns `404`; a Board that exists but belongs to another Project returns `400`.
- Listing preferences returns only entries for Projects where the authenticated user is still a member.
- A stale or cross-Project Board reference is cleared through the preference DELETE endpoint when Project entry detects it, then resolution falls back to the first Board. If cleanup fails and the Project has Boards, the app still opens the first Board without marking it as a default and reports the preference error. If there are no Boards, keep the Owner/Member empty state and include the cleanup error. Preference cleanup must not invalidate or reseed unrelated Project data.
- The client preference query is keyed by user ID and is invalidated after save/clear. Existing sign-out query-cache clearing prevents one user's cached preference from appearing in another session.

## Browser database migration

- Add an empty preference collection to the seed data and a schema-version 1-to-2 migration that preserves all existing Projects, Boards, Columns, Cards, Comments, Notifications, ID counters, and sessions.
- Store the new schema under `dev-platform-demo:db:v2`. When v2 is absent, migrate a valid v1 store, write v2, then remove the v1 key; if v1 is absent or invalid, write a clean seed v2 store. If v2 exists but is invalid, write a seed v2 store successfully before discarding the v1 key, so a failed write does not destroy the legacy copy. Reset writes the clean v2 seed and removes any remaining v1 key only after the v2 write succeeds.
- Validate preference rows independently from core product data. Invalid preference rows are discarded or ignored; they must not cause the full demo database to be reseeded.
- Reset demo data writes a clean v2 seed with no saved preferences and preserves the separate access-token key, matching current reset behavior.

## Scope boundaries

- This change affects only the React demo and its MSW mock API; it does not modify the backend services.
- It does not add Project-level board management pages, board deletion, multi-board views, spreadsheet/table views, or a new UI dependency.
- Existing Project and Board APIs, direct Board URLs, card flows, and Owner-only Board creation remain compatible.

## Affected areas

- App layout, project entry routing, project selection, Board header, Board creation dialog, and responsive navigation styles.
- Board preference API client, React Query hooks/keys, MSW handlers, handler registration, and API tests.
- Seed data, repository schema validation/migration/reset behavior, storage keys, and repository migration tests.
- Browser-level checks for account-menu interactions, Project-to-default resolution, searchable Board switching, default persistence, creation, Kanban layout, and mobile behavior.

## Risks and edge cases

- Project entry must not flash the old Overview or briefly show the wrong Board while preference data loads.
- Switching Projects while queries are in flight must resolve using the route's Project ID and current user, not stale preference data.
- Failed user-initiated Set/Clear writes must keep the prior valid default indicator and show an actionable error. If stale-preference cleanup fails, use the Project-entry behavior defined above: open the fallback only when a Board exists, preserve the role-specific empty state otherwise, never mark the fallback as a saved default, and show the cleanup error.
- Invalid or stale preferences must fall back without discarding user-created demo data.
- Board creation must remain Owner-only in both the UI and mock handler; hiding the button alone is not authorization.
- Dropdown search, default actions, dialog focus, Escape handling, and narrow-screen layout must remain keyboard-usable.

## Verification plan

- Add Node tests for v1-to-v2 migration/data preservation and legacy-key cleanup, preference persistence/reset, authenticated user isolation, membership authorization, invalid Board/Project combinations, and clearing a preference.
- Run the existing Node test suite, ESLint, and production build after implementation.
- Use the browser's primary interface to verify the complete flow: account menu, selecting a Project, default/fallback Board resolution, searching and switching Boards, saving/clearing a default, creating a Board, Kanban layout, and responsive behavior. At narrow widths, confirm the account menu and Board dropdown remain inside the viewport and their actions stay reachable.
- If browser control is unavailable, report that limitation explicitly instead of claiming UI verification.
