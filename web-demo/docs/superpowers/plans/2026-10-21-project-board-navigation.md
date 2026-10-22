# Project and Board Navigation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make Project entry open each user's chosen default Board, provide compact account and searchable Board navigation, and retain a clear responsive Kanban workspace.

**Architecture:** Keep the current React Router and TanStack Query boundaries. Add a user-scoped default-board preference resource to the MSW API and versioned browser repository, then consume that resource from Project entry and Board navigation. Keep Board creation Project-scoped and Owner-only, with one reusable dialog opened from the Board selector or the no-Board empty state.

**Tech Stack:** React 18, React Router 6, TanStack Query 5, Axios, MSW 2, localStorage repository, Node `node:test`, ESLint, Vite, Chrome DevTools MCP.

**Spec:** `docs/superpowers/specs/2026-10-21-project-board-navigation-design.md`

## Global Constraints

- This change affects only the React demo and its MSW mock API; it does not modify the backend services.
- All preference routes are under `/api/v1` and require the existing bearer-token authentication.
- Persist preferences as rows keyed by `(userId, projectId)`, each containing `boardId`; a saved Board must belong to that Project.
- The first Board fallback does not silently save a default.
- Board creation remains Owner-only in both the UI and mock handler.
- Migrate the browser database from schema version 1 to 2 while preserving existing Projects, Boards, Columns, Cards, Comments, Notifications, ID counters, and the separate access-token key.
- There is no Manage boards item or Project-level board management page, no spreadsheet/table view, and no new UI dependency.
- Keep direct Board URLs, existing card flows, the current higher-contrast palette, and responsive horizontal Kanban scrolling.
- The current Git worktree contains unrelated changes and the demo directory is untracked; stage only each task's listed files, never `git add .`, and do not change global Git safety configuration.

## Review Focus

1. **Legacy storage with user data:** valid v1 data must migrate without losing edits; invalid v2 must not resurrect stale v1 after reset; a failed v2 write must leave v1 intact. Pin this in Task 1 migration, recovery, write-failure, and reset tests.
2. **Preference trust boundary:** missing auth, non-members, unknown Board IDs, and cross-Project Board IDs must return the specified errors without changing another user's preference. Pin this in Task 2 handler tests.
3. **Stale default and empty Project:** cleanup failure opens the first Board without a default marker when one exists; with zero Boards it preserves the role-specific empty state and carries the cleanup error into that state. Pin both failure paths in Task 4's pure decision tests and verify the normal Owner/Member empty states in the browser.
4. **Project switch during preference loading:** the next Project must resolve with its own route ID and the signed-in user's preference, never another Project's preference from the same cached list. Pin project scoping in Task 4 and verify rapid Project switching in Task 7.
5. **Small viewport and keyboard use:** account and Board menus must stay in the viewport and their controls remain reachable by keyboard. Verify at 375×812 in Task 7.

---

### Task 1: Migrate the local demo repository to schema v2

**Files:**
- Modify: `src/shared/constants/storageKeys.js`
- Modify: `src/mocks/data/seed.js`
- Modify: `src/mocks/data/databaseRepository.js`
- Modify: `src/mocks/data/databaseRepository.test.js`
- Modify: `src/mocks/testUtils.js`

**Interfaces:**
- Store `defaultBoardPreferences` as rows shaped `{ userId: string, projectId: number, boardId: number }`, unique by `(userId, projectId)`.
- Export `DATABASE_STORAGE_KEY = 'dev-platform-demo:db:v2'` and `LEGACY_DATABASE_STORAGE_KEY = 'dev-platform-demo:db:v1'`.
- `createMockRepository(storage)` requires `getItem`, `setItem`, and `removeItem`; its existing `read`, `transact`, and `reset` API remains unchanged.

- [ ] **Step 1: Write the failing migration and recovery tests**

Add tests named:
- `migrates a valid v1 store to v2 without reseeding product data and removes the legacy key`
- `seeds v2 when v1 is missing or invalid`
- `does not resurrect v1 when an existing v2 store is invalid`
- `keeps v1 intact when writing migrated v2 data fails`
- `keeps v1 intact when writing seed v2 for invalid-v2 recovery fails`
- `keeps v1 intact when reset cannot write the clean v2 seed`
- `discards invalid preference rows without reseeding valid project data`
- `reset clears saved defaults and legacy storage while preserving the access token`

Assert preservation of a modified Project name, existing Board/Card rows and counters; assert v2 schema version and empty preference defaults after migration; update existing seeded-store schema assertions to expect version 2 while keeping explicit v1 fixture assertions at version 1; assert the old key is removed only after the v2 write succeeds and remains when migration, invalid-v2 recovery, or reset writes throw; assert the access-token key is unchanged by reset.

- [ ] **Step 2: Run the repository tests and confirm the new cases fail for the missing v2 behavior**

Run: `node --test src/mocks/data/databaseRepository.test.js`
Expected: the new migration, preference-collection, and legacy-key assertions fail; existing repository tests remain otherwise runnable.

- [ ] **Step 3: Implement v2 seed, migration, preference sanitization, and reset cleanup**

Set the store version to 2; add an empty `defaultBoardPreferences` collection; migrate valid v1 data and counters; discard only invalid preference rows; write v2 before removing v1. If v1 is absent/invalid or existing v2 is invalid, write the clean seed and then remove any legacy key. Update existing schema-version assertions from 1 to 2 (except explicit v1 migration fixtures), and update both in-memory storage adapters with `removeItem`.

- [ ] **Step 4: Run repository tests and confirm migration, invalid-row recovery, write-failure, and reset paths pass**

Run: `node --test src/mocks/data/databaseRepository.test.js`
Expected: all repository tests pass, including the eight migration/recovery cases above.

- [ ] **Step 5: Commit only Task 1 files**

Commit message: `feat: migrate demo storage for board preferences`

---

### Task 2: Add authenticated MSW preference endpoints

**Files:**
- Create: `src/mocks/handlers/preferences.js`
- Create: `src/mocks/handlers/preferences.test.js`
- Modify: `src/mocks/handlers/index.js`

**Interfaces:**
- Export `createPreferenceHandlers(repository, resolveUserFromToken)` and register it with the existing handlers.
- Implement `GET /api/v1/users/me/preferences/default-boards`, `PUT /api/v1/users/me/preferences/default-boards/:projectId`, and `DELETE /api/v1/users/me/preferences/default-boards/:projectId` with the approved request/response contract.
- Resolve `userId` from the bearer token only; use the existing `authenticateRequest`, `authorizeProject`, `parsePositiveId`, `readJson`, and `errorResponse` helpers.

- [ ] **Step 1: Write failing endpoint tests**

Cover these behaviors:
- `lists only the authenticated user's preferences for joined projects`
- `requires a valid bearer token and rejects non-members`
- `sets and replaces a default only when the Board belongs to the Project`
- `returns 404 for missing projects or boards and 400 for malformed or cross-project Board IDs`
- `clears an existing preference and returns 204 when no preference exists`

Assert Owner and Member preferences remain isolated, an outsider cannot read/write a Project preference, and a failed PUT leaves the existing row unchanged.

- [ ] **Step 2: Run preference handler tests and confirm they fail before the handler exists**

Run: `node --test src/mocks/handlers/preferences.test.js`
Expected: tests fail because the preference routes are not registered.

- [ ] **Step 3: Implement preference handlers and register them**

Upsert one row per `(userId, projectId)`. GET returns only rows for Projects where the authenticated user is still a member. DELETE is idempotent after Project authorization. Return 401/403/404/400 for the contract cases and never accept a caller-supplied user ID.

- [ ] **Step 4: Run endpoint tests and confirm auth, isolation, validation, and cleanup pass**

Run: `node --test src/mocks/handlers/preferences.test.js`
Expected: all five preference handler tests pass.

- [ ] **Step 5: Commit only Task 2 files**

Commit message: `feat: add user board preference mock API`

---

### Task 3: Add the preference client and React Query hooks

**Files:**
- Create: `src/features/preferences/services/preferenceApi.js`
- Create: `src/features/preferences/hooks/useDefaultBoards.js`
- Modify: `src/shared/query/queryKeys.js`
- Modify: `src/shared/query/queryKeys.test.js`

**Interfaces:**
- Export `listDefaultBoards()`, `setDefaultBoard(projectId, boardId)`, and `clearDefaultBoard(projectId)` from `preferenceApi.js`.
- Export `useDefaultBoards(userId)`, enabled only when `userId` exists; `useSetDefaultBoard(userId)` accepts `{ projectId, boardId }`; `useClearDefaultBoard(userId)` accepts `projectId`.
- `useDefaultBoards(userId)` returns the API response shape `{ defaultBoards: Array<{ projectId: number, boardId: number }> }`.
- Add `queryKeys.defaultBoards(userId) => ['defaultBoards', userId]`.
- Successful Set/Clear invalidates only the matching signed-in user's default-board query; sign-out continues to clear the whole query cache.

- [ ] **Step 1: Add the failing preference query-key test**

Add `default-board preference keys are scoped to the signed-in user`; assert `queryKeys.defaultBoards('user-1')` equals `['defaultBoards', 'user-1']` and differs for `user-2`.

- [ ] **Step 2: Run the query-key test and confirm it fails because the key is absent**

Run: `node --test src/shared/query/queryKeys.test.js`
Expected: the new key assertion fails while existing query-key tests pass.

- [ ] **Step 3: Implement the Axios API functions, user-scoped hook, and mutation invalidation**

Use the existing `shared/services/axios.js` instance; keep API errors in the existing Axios response shape so pages can render server messages consistently.

- [ ] **Step 4: Run the query-key test and the complete preference handler test file**

Run: `node --test src/shared/query/queryKeys.test.js src/mocks/handlers/preferences.test.js`
Expected: both files pass; the client routes match the exact endpoint paths from Task 2.

- [ ] **Step 5: Commit only Task 3 files**

Commit message: `feat: add board preference query hooks`

---

### Task 4: Resolve Project entry, stale defaults, and the no-Board state

**Files:**
- Create: `src/features/projects/projectEntry.js`
- Create: `src/features/projects/projectEntry.test.js`
- Create: `src/features/projects/pages/ProjectEntryPage.jsx`
- Create: `src/features/boards/components/BoardCreateDialog.jsx`
- Modify: `src/app/router/index.jsx`
- Modify: `src/features/boards/components/BoardHeader.jsx`
- Modify: `src/features/boards/pages/BoardPage.jsx`
- Delete: `src/features/projects/pages/ProjectOverviewPage.jsx`
- Modify: `src/features/boards/boards.css`

**Interfaces:**
- `resolveProjectEntry(boards, projectId, defaultBoards, cleanupError = null)` normalizes the route ID to a number, selects only that Project's preference row, then returns `{ board, isDefault, needsCleanup, cleanupError }`; `board` is the matching saved Board, first Board fallback, or `null` if the Project has no Boards.
- Create `ProjectEntryPage` as the element for `/projects/:projectId`; it consumes project/board queries and the Task 3 preference hooks.
- `BoardCreateDialog({ open, onClose, onCreate, isCreating, error })` uses native `<dialog>`, owns the existing Board name/key fields, and returns focus to its trigger after close; the caller owns the mutation and navigation.
- Board-level back links display “All Projects” and target `/projects`.

- [ ] **Step 1: Write failing pure Project-entry decision tests**

Add tests named:
- `uses a saved Board only when it belongs to the Project board list`
- `uses only the current Project preference when the preference list contains other Projects`
- `uses the first Board without marking it default when no preference exists`
- `returns the first Board and marks cleanup required for a stale preference`
- `keeps the no-Board result empty and marks stale cleanup required`
- `does not mark a stale fallback as default when cleanup fails`
- `keeps the no-Board result and cleanup error when stale cleanup fails`

- [ ] **Step 2: Run Project-entry tests and confirm they fail because the resolver does not exist**

Run: `node --test src/features/projects/projectEntry.test.js`
Expected: the test file fails on the missing resolver export.

- [ ] **Step 3: Implement `resolveProjectEntry` and make its decision tests pass**

Keep the helper pure: it must not save preferences or perform API calls.

- [ ] **Step 4: Run the resolver tests and confirm all seven decisions pass**

Run: `node --test src/features/projects/projectEntry.test.js`
Expected: valid, project-scoped, absent, stale, empty, and both failed-cleanup decisions return the specified fields, including the cleanup error with no Boards.

- [ ] **Step 5: Refactor the Project route into default-board resolution and reusable creation dialog**

Show a loading state until Project, Boards, user, and preference queries resolve. If a query fails, show the existing retryable error state rather than choosing an unknown default. Navigate to a valid saved Board; otherwise use the first Board without saving a default. Clear stale preferences through DELETE; on failure, pass the error to the Board route when a fallback exists or show it beside the role-specific empty state when there are no Boards. Keep the first-Board New Board action Owner-only and navigate to the created Board.

- [ ] **Step 6: Verify Project-entry and direct-link behavior in Chrome DevTools**

Check Project list card and global Project selector both resolve the saved default; no preference opens the first Board; stale preference cleanup failure with Boards opens the first Board without a default badge; zero Boards show the correct Owner/Member empty state; the Task 4 unit test confirms a cleanup failure is carried into that zero-Board state; direct Board URLs still open their requested Board; back links open All Projects.

- [ ] **Step 7: Commit only Task 4 files**

Commit message: `feat: route project entry to its default board`

---

### Task 5: Compact the account menu and remove project tabs

**Files:**
- Create: `src/app/layout/AccountMenu.jsx`
- Modify: `src/app/layout/AppLayout.jsx`
- Modify: `src/index.css`

**Interfaces:**
- `AccountMenu({ user, onSignOut })` renders an avatar button and a menu with the current user's name, email, and Sign out; Escape closes the menu and returns focus to the avatar, and outside-click listeners are removed on close/unmount.
- AppLayout keeps the workspace selector, reset action, and NotificationPanel; the project bar displays only Project mark/name/role and has no Overview or Board links. Since it has no destinations, render the project identity as a labelled context container rather than an empty `<nav>` landmark.

- [ ] **Step 1: Record the failing UI baseline in Chrome DevTools**

On the current desktop app, confirm `.account-summary` permanently renders full account details and the project bar contains Overview/Board links. Save the snapshot/evaluate result for the implementation review.

- [ ] **Step 2: Implement the avatar menu and project-only second bar**

Close the menu on Escape, outside click, or sign-out; clean up all document listeners on close/unmount. Preserve the current sign-out handler and notification/reset behavior.

- [ ] **Step 3: Verify the account and navigation flow in Chrome DevTools**

Confirm the default header shows only the avatar trigger, the menu displays name/email/sign-out, Escape and outside click close it, sign-out reaches `/login`, and the second bar contains no tabs. Repeat at desktop and 375×812.

- [ ] **Step 4: Commit only Task 5 files**

Commit message: `feat: compact account and project navigation`

---

### Task 6: Add searchable Board switching and create actions

**Files:**
- Create: `src/features/boards/components/BoardSelector.jsx`
- Modify: `src/features/boards/components/BoardHeader.jsx`
- Modify: `src/features/boards/pages/BoardPage.jsx`
- Modify: `src/features/boards/boards.css`
- Modify: `src/features/projects/pages/ProjectEntryPage.jsx`
- Modify: `src/features/boards/components/BoardCreateDialog.jsx`

**Interfaces:**
- `BoardSelector({ boards, currentBoardId, defaultBoardId, canCreate, onSelectBoard, onSetDefault, onClearDefault, onCreateBoard })` owns dropdown/search state; board navigation and default mutations remain callbacks.
- Search matches Board name or key. Each result has separate navigation and Set/Clear buttons with no nested interactive elements; Clear replaces Set on the saved-default row. Use Tab/Enter and Escape for keyboard operation and expose expanded state to assistive technology.
- The bottom `+ New board` action is visible only to Owners and opens the shared BoardCreateDialog; a created Board opens directly and does not become default automatically.

- [ ] **Step 1: Record failing Board-selector UI checks before implementation**

With a seeded Project after Task 5, verify the Board name is not searchable and no dropdown footer creation action exists yet. The second bar should already contain no project tabs.

- [ ] **Step 2: Implement searchable Board selector and saved-default actions**

Use the current Project's Board list and user-scoped preference hooks. Keep selection separate from Set/Clear actions; display pending/error states without changing the previous valid default indicator on failed writes.

- [ ] **Step 3: Wire Owner-only `+ New board` to the shared dialog**

Pass the existing `useCreateProjectBoard(projectId)` mutation to the dialog; after success navigate to the new Board. Keep the Owner check in both UI and existing MSW create handler.

- [ ] **Step 4: Verify search, switch, preference, and create flows in Chrome DevTools**

Search by name and key, switch Boards, Set a different Board as default without navigating, reload `/projects/:projectId` and confirm the saved Board opens, Clear default and confirm the first-Board fallback, then create a Board from the footer and confirm it opens without changing the saved default.

- [ ] **Step 5: Commit only Task 6 files**

Commit message: `feat: add searchable board navigation`

---

### Task 7: Frame the Kanban workspace and run full integration verification

**Files:**
- Modify: `src/features/boards/pages/BoardPage.jsx`
- Modify: `src/features/boards/boards.css`
- Modify: `src/index.css` only if menu/viewport containment needs a shared layout adjustment

- [ ] **Step 1: Record the current Board canvas baseline**

Capture the seeded Board at desktop width and note the current separation between Board identity, toolbar, and Columns.

- [ ] **Step 2: Frame the Board identity, toolbar, and columns as one workspace**

Use existing color/border tokens, preserve column/card controls, and keep overflow inside the Kanban scroller rather than the page.

- [ ] **Step 3: Run all automated checks**

Run: `npm test`
Expected: all Node tests pass.

Run: `npm run lint`
Expected: ESLint exits 0 with no errors.

Run: `npm run build`
Expected: Vite completes the production build with exit code 0.

- [ ] **Step 4: Verify the full user flow and responsive behavior in Chrome DevTools**

At desktop, test account menu, project switching while preference data is pending, saved/default/fallback navigation, stale cleanup success/failure, searchable Board navigation, create/clear default, new Board creation, and existing card flows. Confirm Network shows the expected preference GET/PUT/DELETE routes and the Console has no new errors. At 375×812, verify both menus fit the viewport, keyboard focus/escape works, page width does not overflow, and Kanban columns scroll within the Board canvas.

- [ ] **Step 5: Commit only Task 7 files**

Commit message: `style: clarify kanban board workspace`
