# Kanban Web Demo Implementation Plan

> **Phạm vi backend (cập nhật 2026-10-29):** Kế hoạch này chỉ triển khai React/MSW demo. Bearer identity, Owner-only UI và Project creation trong các bước dưới đây là hành vi mock; chúng không thay thế backend contract về Account `ACTIVE`, identity Gateway hoặc quyền `SYSTEM_ADMIN`. Demo không mô phỏng Account snapshot/revocation. Tham chiếu [architecture decisions](../../project/architecture-decisions.md) và [Kanban API](../../api/kanban-api.md). Mock Board-first routes có thể khác backend Project-scoped Card routes.

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Build a standalone React demo for Project-first Kỳ 1 Kanban, using MSW as a persistent mock HTTP backend.

**Architecture:** Create `web-demo/` as an independent Vite app. Feature pages call feature API modules through one Axios client; MSW handlers authorize each request and use a versioned localStorage repository. Keep the current backend untouched.

**Tech Stack:** JavaScript/JSX, React 18, Vite 5, React Router 6, TanStack Query 5, Axios 1, Bootstrap 5, MSW 2, ESLint, and Node's built-in test runner.

**Spec:** `docs/superpowers/specs/2026-10-21-kanban-web-demo-design.md`

## Global Constraints

- Build the app under `web-demo/`; do not add it as a Maven module or change backend services/Gateway.
- Follow the reference frontend's JavaScript/JSX, feature folders, router, Axios service, and Bootstrap conventions.
- Run every `npm` command listed below from the `web-demo/` directory.
- All product data requests go through feature API functions, Axios with `/api/v1`, and MSW.
- Send bearer auth from the demo session; never accept a client-supplied `userId` as the caller identity.
- Use positive numeric `Long` IDs for Project, Board, Column, Card, Comment, and Notification; use UUIDs for user IDs.
- Keep `Board.key`; do not add `Project.key` or change existing Board/Card identifier semantics.
- Project roles are `OWNER` and `MEMBER`. New Projects start with only their creator as Owner; do not add invitations or member administration.
- Use localStorage key `dev-platform-demo:db:v1` for the product mock database and `dev-platform-demo:access-token` for the demo session.
- Require Node.js 18 or newer; use ESM so Vite and Node's built-in test runner can load the same source modules.
- Column membership determines Kanban placement. Card `status` stays independent and is not inferred from Column names or shown as a separate badge.
- Seed an unread Notification for the demo Owner. For the demo, `MOVED` goes to Project members other than the actor.
- Keep the mock single-browser; do not implement WebSocket or cross-browser sync.

## Review Focus

1. **Identity and Project boundary:** reject missing/invalid bearer tokens with 401 and non-member Project access with 403; cover token-derived identity and nested Board access in Task 2/3 handler tests.
2. **Project membership lifecycle:** a newly created Project has only its creator, while the sample Project has assignable Members; cover this in Task 2 repository/API tests and Task 4 assignment tests.
3. **Card move integrity:** reject Columns from another Board, normalize source/destination positions, and leave `status` unchanged; cover in Task 4 handler tests.
4. **Notification recipients:** prevent self-notification, deduplicate mentions, restrict reads to the recipient, and deliver demo `MOVED` notifications only to other members; cover in Task 4/5 handler tests.
5. **Persisted state and context switches:** recover from invalid/version-mismatched localStorage, keep session separate from product data, restore the seed on reset, and avoid stale Board/Card detail when switching Projects; cover in Task 2 tests and Task 6 browser flow.

---

## File Map

- `web-demo/package.json`, `vite.config.js`, `eslint.config.js`, `index.html`: app scripts and Vite/ESLint setup, modeled on the reference frontend.
- `web-demo/src/main.jsx`, `src/app/App.jsx`, `src/app/router/index.jsx`, `src/app/providers/AppProviders.jsx`: MSW startup, routing, and TanStack Query provider.
- `web-demo/src/app/layout/AppLayout.jsx`, `src/index.css`: shared navigation shell and responsive base styles.
- `web-demo/src/shared/services/axios.js`, `src/shared/constants/storageKeys.js`, `src/shared/query/queryKeys.js`: HTTP client, storage names, and stable query keys.
- `web-demo/src/features/auth/**`: login page, auth API, current-user query, and access-token lifecycle.
- `web-demo/src/features/projects/**`: Project API, query hooks, list/create/overview screens.
- `web-demo/src/features/boards/**`: Board/Column API, handlers, Kanban page and Column components.
- `web-demo/src/features/cards/**`: Card/comment API, handlers, create/detail/move/assign/comment UI.
- `web-demo/src/features/notifications/**`: notification API, handlers, list/read UI.
- `web-demo/src/mocks/browser.js`, `src/mocks/handlers/**`, `src/mocks/data/**`: browser worker, resource handlers, seed, repository factory, and shared browser repository instance.
- `web-demo/public/mockServiceWorker.js`: MSW browser worker script generated by the MSW CLI.
- `web-demo/src/mocks/**/*.test.js`: Node test-runner coverage for repository and HTTP handlers.
- `web-demo/src/shared/query/queryKeys.test.js`: Project/Board/Card query-scope regression checks.
- `web-demo/README.md`: run instructions, demo credentials, reset behavior, and known mock/backend differences.

## Task 1: Scaffold the standalone React app

**Files:**
- Create: `web-demo/package.json`
- Create: `web-demo/package-lock.json`
- Create: `web-demo/index.html`
- Create: `web-demo/vite.config.js`
- Create: `web-demo/eslint.config.js`
- Create: `web-demo/src/main.jsx`
- Create: `web-demo/src/index.css`
- Create: `web-demo/src/app/App.jsx`
- Create: `web-demo/src/app/router/index.jsx`
- Create: `web-demo/src/app/providers/AppProviders.jsx`
- Create: `web-demo/src/app/layout/AppLayout.jsx`

**Interfaces:**
- `AppProviders({ children })` creates one QueryClient and wraps the router in QueryClientProvider.
- `AppLayout` provides the responsive Project navigation, top bar, main content outlet, and demo reset/sign-out slots.
- `App` renders the configured React Router tree.

- [x] **Step 1: Create the app package and scripts.** Use ESM (`"type": "module"`) and require Node.js 18 or newer. Use the reference app's dependency ranges for React, Vite, `@vitejs/plugin-react`, React Router, TanStack Query, Axios, Bootstrap, ESLint, and its React lint plugins; add MSW 2 and the `node --test` script. Add `dev`, `build`, `preview`, `lint`, and `test` scripts, then run `npm install` to create the lockfile.
- [x] **Step 2: Create the Vite React entry and app shell.** Add the root HTML element, React plugin config, app entry, QueryClient provider, placeholder routes, and responsive base CSS. Load Bootstrap CSS before the app stylesheet.
- [x] **Step 3: Verify the scaffold.** Run `npm run lint` and `npm run build` from `web-demo/`.
  - Expected: both exit successfully; Vite outputs `web-demo/dist/`.

## Task 2: Implement mock persistence, session, authentication, and Projects

**Files:**
- Create: `web-demo/src/shared/constants/storageKeys.js`
- Create: `web-demo/src/shared/services/axios.js`
- Create: `web-demo/src/shared/query/queryKeys.js`
- Create: `web-demo/src/features/auth/session.js`
- Create: `web-demo/src/features/auth/services/authApi.js`
- Create: `web-demo/src/features/auth/hooks/useCurrentUser.js`
- Create: `web-demo/src/features/auth/pages/LoginPage.jsx`
- Create: `web-demo/src/app/router/ProtectedRoute.jsx`
- Create: `web-demo/src/features/projects/services/projectApi.js`
- Create: `web-demo/src/features/projects/hooks/useProjects.js`
- Create: `web-demo/src/features/projects/pages/ProjectListPage.jsx`
- Create: `web-demo/src/features/projects/components/ProjectCreateForm.jsx`
- Create: `web-demo/src/mocks/data/seed.js`
- Create: `web-demo/src/mocks/data/databaseRepository.js`
- Create: `web-demo/src/mocks/data/mockRepository.js`
- Create: `web-demo/src/mocks/data/databaseRepository.test.js`
- Create: `web-demo/src/mocks/handlers/auth.test.js`
- Create: `web-demo/src/mocks/handlers/projects.test.js`
- Create: `web-demo/src/mocks/handlers/auth.js`
- Create: `web-demo/src/mocks/handlers/projects.js`
- Create: `web-demo/src/mocks/handlers/index.js`
- Create: `web-demo/src/mocks/browser.js`
- Create: `web-demo/public/mockServiceWorker.js` (generated by `npx msw init public --save`)
- Modify: `web-demo/src/main.jsx`
- Modify: `web-demo/src/app/router/index.jsx`

**Interfaces:**
- `createMockRepository(storage)` accepts the `getItem`/`setItem` storage interface and returns `read()`, `transact(mutator)`, and `reset()`; `transact` runs `(draft, allocateId) => result` on a copy, persists only after the callback succeeds, and returns its result.
- `transact(mutator)` passes `(draft, allocateId)`; `allocateId(type)` increments the persisted counter for that resource type. Project, Board, Column, Card, Comment, and Notification IDs stay positive, unique within their resource type, and monotonic across reloads; reset restores counters above the seed IDs. User IDs remain seeded UUIDs.
- `mockRepository.js` creates one browser repository instance for handlers and demo controls; tests use the factory with isolated in-memory storage.
- `createHandlers(repository, resolveUserFromToken)` returns the composed MSW handlers; the browser supplies the seeded Owner resolver and handler tests inject Member and non-member identities.
- `getAccessToken()`, `setAccessToken(token)`, and `clearSession()` manage only `dev-platform-demo:access-token`.
- Auth API exports `login({ email, password })` and `getCurrentUser()`; `useCurrentUser()` queries `/users/me` for the signed-in user identity.
- `useProjects.js` exports `useProjects()`, `useProject(projectId)`, and `useProjectMembers(projectId)` so pages share Project-scoped TanStack Query entries.
- Project API exports `listProjects()`, `getProject(projectId)`, `createProject({ name })`, and `listProjectMembers(projectId)`.
- `queryKeys` exports `projects`, `currentUser`, `project(projectId)`, `projectMembers(projectId)`, `projectBoards(projectId)`, `board(boardId)`, `cards(boardId)`, `card(cardId)`, `comments(cardId)`, and `notifications(userId)`; `projects` and `currentUser` are stable arrays and resource keys are functions of their IDs.

- [x] **Step 1: Write repository tests.** In `databaseRepository.test.js`, cover empty storage seeds the demo Owner/Project/Boards/Members/unread Notification; valid versioned data reloads; invalid JSON or an unsupported version resets to seed; reset restores product data without clearing the separate access-token key; a throwing `transact` callback does not persist a partial update; and newly allocated IDs remain positive/unique across reloads while reset starts above seed IDs.
- [ ] **Step 2: Run the repository tests and confirm they fail.** Run `npm test -- src/mocks/data/databaseRepository.test.js`.
  - Expected: FAIL because `createMockRepository` and the seed are not implemented.
- [x] **Step 3: Implement the versioned seed and repository.** Store product data and per-resource ID counters under `dev-platform-demo:db:v1`; initialize counters above seed IDs; expose `read`, `transact`, and `reset`; keep session storage separate.
- [x] **Step 4: Run the repository tests and confirm they pass.** Run `npm test -- src/mocks/data/databaseRepository.test.js`.
  - Expected: PASS for seed, reload, invalid-store recovery, and reset/session separation.
- [x] **Step 5: Write auth/Project handler tests.** Cover valid/invalid login, `GET /users/me` resolving bearer identity, missing/invalid token returning 401, Owner and Member successfully listing/reading their Project and members, non-member access returning 403, Project creation assigning the bearer user as sole Owner, and ignoring any body-supplied `userId`.
- [x] **Step 6: Implement the Axios client and auth/Project handlers.** Use a request interceptor to attach the stored Bearer token; return the documented error shape. Create the shared browser repository, generate `public/mockServiceWorker.js` with `npx msw init public --save`, and await worker startup before React mounts. New Project creation adds only the caller as Owner; the application does not accept a client-supplied identity.
- [x] **Step 7: Implement login, route protection, and Project list/create UI.** Add loading, empty, validation, and API error states. Login writes the access token; logout clears the token and QueryClient cache but leaves product data. Protect Project routes and redirect signed-out users to login; use `useCurrentUser()` for the identity needed by the shell and notification query.
- [x] **Step 8: Run focused tests, linter, and build.** Run `npm test`, `npm run lint`, and `npm run build`.
  - Expected: tests pass; lint/build exit successfully.

## Task 3: Implement Project detail, Boards, and Columns

**Files:**
- Create: `web-demo/src/features/projects/pages/ProjectOverviewPage.jsx`
- Create: `web-demo/src/features/boards/services/boardApi.js`
- Create: `web-demo/src/features/boards/hooks/useBoards.js`
- Create: `web-demo/src/features/boards/pages/BoardPage.jsx`
- Create: `web-demo/src/features/boards/components/BoardHeader.jsx`
- Create: `web-demo/src/features/boards/components/KanbanColumns.jsx`
- Create: `web-demo/src/features/boards/components/Column.jsx`
- Create: `web-demo/src/features/boards/components/ColumnCreateForm.jsx`
- Create: `web-demo/src/mocks/handlers/boards.js`
- Create: `web-demo/src/mocks/handlers/boards.test.js`
- Modify: `web-demo/src/mocks/handlers/index.js`
- Modify: `web-demo/src/features/projects/services/projectApi.js`
- Modify: `web-demo/src/app/router/index.jsx`
- Create: `web-demo/src/features/boards/boards.css`

**Interfaces:**
- Board API exports `listProjectBoards(projectId)`, `createProjectBoard(projectId, { name, key, columns })`, `getBoard(boardId)`, `addColumn(boardId, { name })`, and `reorderColumns(boardId, orderedColumnIds)`.
- Routes are `/projects/:projectId` and `/projects/:projectId/boards/:boardId`.
- Board query key is `['board', boardId]`; Project Board list key is `['projectBoards', projectId]`.

- [x] **Step 1: Write Board/Column handler tests.** Cover Owner and Member successfully reading their Project's Board list and Board details; a non-member receives 403 for direct/nested Board reads; Owner can create a Board and manage Columns; Member receives 403 for those actions; omitted Columns create `To Do / In Progress / Done`; duplicate or invalid Board keys return 409/400; reorder rejects IDs outside the Board.
- [ ] **Step 2: Run the Board/Column tests and confirm they fail.** Run `npm test -- src/mocks/handlers/boards.test.js`.
- [x] **Step 3: Implement Board and Column API functions and MSW handlers.** Resolve each Board's Project before checking membership. Preserve Board key validation and numeric IDs. Use the nested Project route for Board creation.
- [x] **Step 4: Implement Project overview and Board navigation.** Show the Project's Boards to Owner and Member. Show the create-Board and add/reorder-Column controls only to Owner; Member can open and read Boards and use Card actions.
- [x] **Step 5: Run focused tests, linter, and build.** Run `npm test`, `npm run lint`, and `npm run build`.
  - Expected: handler tests pass; app compiles and lint exits successfully.

## Task 4: Implement Cards, movement, assignment, and comments

**Files:**
- Create: `web-demo/src/features/cards/services/cardApi.js`
- Create: `web-demo/src/features/cards/hooks/useCards.js`
- Create: `web-demo/src/features/cards/components/CardItem.jsx`
- Create: `web-demo/src/features/cards/components/CardCreateForm.jsx`
- Create: `web-demo/src/features/cards/components/CardDetailPanel.jsx`
- Create: `web-demo/src/features/cards/components/CardMoveControl.jsx`
- Create: `web-demo/src/mocks/handlers/cards.js`
- Create: `web-demo/src/mocks/handlers/cards.test.js`
- Modify: `web-demo/src/mocks/handlers/index.js`
- Modify: `web-demo/src/features/boards/components/KanbanColumns.jsx`
- Modify: `web-demo/src/features/boards/components/Column.jsx`
- Modify: `web-demo/src/features/boards/pages/BoardPage.jsx`
- Create: `web-demo/src/features/cards/cards.css`

**Interfaces:**
- Card API exports `listCards(boardId)`, `getCard(cardId)`, `createCard(boardId, payload)`, `moveCard(cardId, { toColumnId, toPosition })`, `assignCard(cardId, { assigneeId })`, `listComments(cardId)`, and `addComment(cardId, payload)`.
- Card query key is `['cards', boardId]`; detail/comments keys include `cardId`.
- A Card's `columnId` and `position` determine its Board placement; `status` is preserved independently.

- [x] **Step 1: Write Card handler tests.** Cover create/list/detail fields (`columnId`, `title`, `description`, `assigneeId`, and `dueDate`); verify detail returns `description` and `dueDate`; move within and between Columns with contiguous source/destination positions; reject a destination Column from another Board; leave `status` unchanged; verify a Project Member can create/move/assign/comment; and verify non-members receive 403 for Card list/read/create/move/assign and comment list/create routes after resolving Card → Board → Project. Also reject an assignee or mentioned user outside the Project; return comment history; store unique `mentionedUserIds` internally while keeping comment response fields unchanged; create ASSIGNED/MENTIONED/MOVED notifications with the approved recipient rules, including no self-notification.
- [ ] **Step 2: Run the Card tests and confirm they fail.** Run `npm test -- src/mocks/handlers/cards.test.js`.
- [x] **Step 3: Implement Card/comment handlers and API functions.** Keep request payloads from the approved spec; resolve Card → Board → Project before authorization. Movement updates `columnId/position` only. Assignment, mention, and move add Notifications using the approved recipient policy.
- [x] **Step 4: Implement the Kanban Card UI.** Add Card creation with title/description/due date and optional assignee, a detail panel showing description/due date, assignment picker loaded from Project members, comment list/form with a Project-member mention selector, and movement between Columns. Use native HTML drag/drop plus a keyboard-accessible Move-to control; do not add a drag/drop dependency.
- [x] **Step 5: Run focused tests, linter, and build.** Run `npm test`, `npm run lint`, and `npm run build`.
  - Expected: handler tests pass; the Board/Card flow compiles and lint exits successfully.

## Task 5: Implement Notifications

**Files:**
- Create: `web-demo/src/features/notifications/services/notificationApi.js`
- Create: `web-demo/src/features/notifications/hooks/useNotifications.js`
- Create: `web-demo/src/features/notifications/components/NotificationPanel.jsx`
- Create: `web-demo/src/mocks/handlers/notifications.js`
- Create: `web-demo/src/mocks/handlers/notifications.test.js`
- Modify: `web-demo/src/mocks/handlers/index.js`
- Modify: `web-demo/src/app/layout/AppLayout.jsx`
- Create: `web-demo/src/features/notifications/notifications.css`

**Interfaces:**
- Notification API exports `listMyNotifications({ unreadOnly })` and `markNotificationRead(notificationId)`.
- Notification query key is `['notifications', currentUserId]`.
- Only the bearer-token user's Notifications are returned or updated.

- [x] **Step 1: Write Notification handler tests.** Cover Owner sees the seeded unread Notification; read changes only the recipient's item; another user's item returns 403; `unreadOnly=true` filters read items; ASSIGNED, MENTIONED, and MOVED entries retain the agreed fields and recipient behavior.
- [ ] **Step 2: Run the Notification tests and confirm they fail.** Run `npm test -- src/mocks/handlers/notifications.test.js`.
- [x] **Step 3: Implement Notification handlers and API functions.** The client can list/read but cannot create Notifications. Keep the unread query optional and return the existing notification shape.
- [x] **Step 4: Implement the Notification panel.** Show unread/read state, title, time, and a mark-read action; refresh the query after a successful update.
- [x] **Step 5: Run focused tests, linter, and build.** Run `npm test`, `npm run lint`, and `npm run build`.
  - Expected: notification ownership and filtering tests pass; lint/build exit successfully.

## Task 6: Integrate lifecycle, responsive layout, and demo instructions

**Files:**
- Modify: `web-demo/src/app/layout/AppLayout.jsx`
- Modify: `web-demo/src/app/router/index.jsx`
- Modify: `web-demo/src/app/providers/AppProviders.jsx`
- Modify: `web-demo/src/index.css`
- Create: `web-demo/src/shared/query/queryKeys.test.js`
- Create: `web-demo/src/mocks/demoControls.js`
- Create: `web-demo/README.md`

**Interfaces:**
- `resetDemoData()` is an isolated mock-only control used by the app shell; feature components never import the mock repository.
- Project switching uses Project-scoped query keys and closes Card detail when its Board is outside the active Project.
- Logout clears the access token and QueryClient cache while preserving the mock database.

- [x] **Step 1: Write query-scope regression tests.** In `queryKeys.test.js`, assert Project list/detail/member/Board keys use the expected IDs and two different Project IDs produce different Project-scoped keys.
- [ ] **Step 2: Run the query-scope tests and confirm they fail.** Run `npm test -- src/shared/query/queryKeys.test.js`.
- [x] **Step 3: Implement the app-shell lifecycle controls.** Add sign-out, isolated Reset demo data through `demoControls.js`, Project switching, query-cache cleanup after reset/sign-out, and not-found/401/403/error states. Reset restores product data to the seed while preserving the current access token; navigating to another Project must unmount any Card detail from the previous Board.
- [x] **Step 4: Finish responsive styling and README.** Keep navigation collapsible and Kanban columns horizontally usable at narrow widths. Document install/start commands, seeded account, feature scope, reset behavior, and current backend auth/API differences.
- [x] **Step 5: Run the approved verification flow.** Run `npm test`, `npm run lint`, and `npm run build`; then manually exercise: sign in → open sample Project → create/open Board → create Card with description/due date → move Card → assign seeded Member/comment → mark Notification read → create another Project and Board inside it → reopen a Card detail in the sample Project → switch to the new Project while detail is open and confirm it closes → reload → reset seed.
  - Expected: all focused tests pass, lint/build exit successfully, mock requests stay under `/api/v1`, and data persists until reset.

## Execution Notes

- Evidence note: the five TDD red-run steps above are unchecked because no red-run output was retained to verify them. The SDD progress ledger records the tests, implementation, and final passing verification.

- The existing React reference uses `src/app/router/index.jsx`, `src/shared/services/axios.js`, feature-oriented `src/features/**`, Bootstrap, and TanStack Query. Mirror those boundaries without copying its ecommerce layouts or auth refresh-token behavior.
- The Dev-Platform Gateway currently routes only User/Auth, Board/Card, WebSocket, and Notification paths. Project routes belong only to MSW in this task; do not change Gateway routes or backend controllers.
- The current Board API uses a temporary `X-User-Id` stub and current Card endpoints have incomplete auth enforcement. Demo handlers intentionally use the approved Bearer-token contract and enforce Project membership.
- The test commands above are plan steps; none have been run while authoring this plan.
