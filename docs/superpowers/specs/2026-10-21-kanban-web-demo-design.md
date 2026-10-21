# Kanban Web Demo Design

**Status:** Draft for user review

**Date:** 2026-10-21

## Purpose

Create a separate React web demo so the product owner can inspect the Project-first Kỳ 1 Kanban experience before connecting a real backend. Feature code will call an HTTP API through Axios; Mock Service Worker (MSW) will intercept those requests and return seeded data.

## Agreed direction

- Put the complete demo in a new `web-demo/` folder under Dev-Platform.
- Use React and a feature-oriented layout informed by `C:/full/SBA301/ecommerce/frontend`.
- Use MSW to simulate HTTP APIs; do not let UI components import mock data directly.
- Cover Projects and Kỳ 1 Kanban. Git, pull requests, Workflow, Agents, and Actions are outside this demo.
- A Project contains multiple Boards. Board and Project are one product boundary; they are not separate services.

### Current code and target topology

The repository currently has an API Gateway plus `user-service`, `board-service`, `task-service`, and `notification-service`. This demo does not consolidate or modify those backend modules.

The agreed Kỳ 1 target is one deployable `project-service` behind the Gateway. It contains internal modules for identity/user, project/membership, board/columns, cards/comments/realtime, and notifications. Kỳ 2 can add separate `git-core-service` and `workflow-service` deployables. They refer to stable `projectId` and `cardId` values through APIs/events, without database foreign keys into Project storage.

## Product model and permissions

```text
Project
├── Members (Owner, Member)
└── Boards
    └── Columns
        └── Cards
            └── Comments
```

- Project owns its name, creator, and membership. Use a positive numeric `Long` as its stable ID, matching the current Board/Card ID convention; later Kỳ 2 services refer to it as a scalar value.
- Board belongs to one Project and owns its Columns. Project membership is inherited by its Boards and Cards.
- Card belongs to one Board and Column; Comments belong to Cards.
- Preserve the current `Board.key` field and its uniqueness rule. Do not introduce `Project.key` or change Board/Card identifiers in this demo. A future project-wide work-item key can be decided with the Kỳ 2 API.
- Project, Board, and Card remain separate domain aggregates even when deployed in one service.

The mock API derives the current user from the bearer token; callers must not send `userId` to choose their identity. An Owner can create Projects and Boards and manage Columns. A Member can view the Project and Boards and use Kanban card actions: create, move, assign, and comment. Both roles can read Notifications for themselves. Invitations, role administration, and permission editing are not included.

Seed one Owner account and one sample Project with multiple Boards, several Project members (including Members usable in the assignee picker), and at least one unread Notification addressed to the demo Owner. A newly created Project starts with only its creator as Owner because invitations/member administration are out of scope; its assignee picker therefore has no other Members. The demo flow uses the sample Project for assignment and uses the new Project to demonstrate Project/Board creation. Project rename and delete are not included.

## User flow and screens

1. Sign in with the preconfigured demo account and view the Projects list.
2. Open the sample Project, see its Boards, and create a Board.
3. Open a Board; create a Card, move it between Columns, assign a seeded Project member, and add/read comments.
4. View Notifications and mark an item as read.
5. Create a separate Project and Board to inspect the Project creation flow.
6. Reload to confirm mock data persists, or use Reset demo data to restore the seed.

The main layout is a desktop-first workspace with project navigation, a compact top bar, and horizontally scrollable Kanban columns. On narrow screens, project navigation collapses while columns remain horizontally scrollable.

## Frontend architecture

Use Vite, React Router, TanStack Query, Axios, and MSW. Keep feature API functions separate from UI components and place shared infrastructure under `app/` and `shared/`.

```text
web-demo/
├── src/
│   ├── app/                 # router, providers, app shell
│   ├── features/
│   │   ├── auth/
│   │   ├── projects/        # project list, overview, board list
│   │   ├── boards/          # Kanban board and columns
│   │   ├── cards/           # card detail, assignment, comments
│   │   └── notifications/
│   ├── shared/              # Axios client, UI, types, utilities
│   └── mocks/               # MSW worker, handlers, mock repository, seed
└── package.json
```

The Axios client has a `/api/v1` base URL. Feature API functions are the only way UI features access server data. MSW is enabled for this demo; switching to a real backend should replace the mock handlers/repository without changing feature components.

## Mock HTTP API contract

The paths are resource-oriented and do not expose service names.

| Method | Endpoint | Purpose |
|---|---|---|
| `POST` | `/api/v1/auth/login` | Sign in as the seeded user |
| `GET` | `/api/v1/users/me` | Read the current user |
| `GET`, `POST` | `/api/v1/projects` | List and create Projects |
| `GET` | `/api/v1/projects/{projectId}` | Read Project details |
| `GET` | `/api/v1/projects/{projectId}/members` | List members for the assignee picker |
| `GET`, `POST` | `/api/v1/projects/{projectId}/boards` | List and create Boards in a Project |
| `GET` | `/api/v1/boards/{boardId}` | Read a Board and its Columns |
| `POST` | `/api/v1/boards/{boardId}/columns` | Add a Column |
| `PATCH` | `/api/v1/boards/{boardId}/columns/order` | Reorder Columns |
| `GET`, `POST` | `/api/v1/boards/{boardId}/cards` | List and create Cards |
| `GET` | `/api/v1/cards/{cardId}` | Read Card details for the detail view |
| `PATCH` | `/api/v1/cards/{cardId}/move` | Move a Card to another Column/position |
| `PATCH` | `/api/v1/cards/{cardId}/assignee` | Assign a Project member |
| `GET`, `POST` | `/api/v1/cards/{cardId}/comments` | Read and add comments |
| `GET` | `/api/v1/notifications/me` (optional `?unreadOnly=true`) | List the current user's Notifications |
| `PATCH` | `/api/v1/notifications/{notificationId}/read` | Mark a Notification as read |

### Request and response shapes

- Login accepts `{ "email": "...", "password": "..." }` and returns `{ "accessToken": "...", "tokenType": "Bearer" }`. Protected requests send `Authorization: Bearer <accessToken>`; `GET /users/me` returns `{ id, email, fullName }`.
- Project, Board, Column, Card, Comment, and Notification IDs are positive numeric `Long` values on the wire. User IDs (`ownerId`, `assigneeId`, `authorId`, member `id`) are UUIDs. Frontend code treats IDs as opaque values and does not generate them.
- Project creation accepts `{ "name": "..." }` and returns `{ id, name, ownerId, role: "OWNER", createdAt }`. Project list items also include `boardCount` and the caller's `role`. Project detail returns the Project identity, owner, and caller role.
- Project member items are `{ id, email, fullName, role }`, with role `OWNER` or `MEMBER`. The new Project's initial member list contains only its creator.
- Board creation accepts `{ "name": "...", "key": "ABC", "columns": ["To Do", "In Progress", "Done"] }`; `columns` may be omitted for the existing default. Its response includes `id, projectId, name, key, ownerId, columns`. Keep the existing Board key validation and uniqueness rule.
- Column creation accepts `{ "name": "Review" }`; reorder accepts `{ "orderedColumnIds": [10, 11, 12] }`.
- Card creation keeps the current request fields: `columnId, title, description, assigneeId, dueDate`. Move accepts `{ "toColumnId": 11, "toPosition": 0 }`; assignment accepts `{ "assigneeId": "..." }`.
- Card detail returns the current Card fields plus `description` and `dueDate`, which the existing create request already accepts. The existing list/create response fields remain `id, boardId, columnId, title, status, position, assigneeId`.
- For Kanban placement, `columnId` is authoritative. Moving a Card changes `columnId` and `position` only, matching the current `CardAggregate.move`; `status` remains the existing independent field and is not inferred from Column names or shown as a separate badge in this demo.
- Comment creation keeps `{ "content": "...", "mentionedUserIds": ["..."] }`. Comment items return `id, cardId, authorId, content, createdAt`, matching the current response shape. The mock stores `mentionedUserIds` internally to create `MENTIONED` Notifications.
- Notification items retain `id, type, title, cardId, boardId, read, createdAt`; supported types remain `ASSIGNED`, `MENTIONED`, and `MOVED`. Assignment notifies the new assignee unless that is the actor. Mention notifies each unique mentioned Project member except the author. A move notifies other Project members except the actor; this fan-out is a demo policy because current API docs define the `MOVED` type but not its recipients. Only a Notification's recipient can mark it read; another user's Notification returns `403`. The client cannot create Notifications directly.
- Errors use the existing `code, message, timestamp, path` shape with representative `400`, `401`, `403`, `404`, and `409` cases.

The mock resolves each Board to its Project and each Card to its Board and Project before checking membership. Missing/invalid bearer token returns `401`; an existing Project the user cannot access returns `403`; an unknown resource returns `404`. A Column used by a Card must belong to that Card's Board. An assignee and mentioned user must be members of the same Project. Notification list and read operations are scoped to the bearer-token user. The `403` behavior is part of the handler contract, but the normal demo UI flow does not exercise it because this scope has no second sign-in identity or member administration.

Project/member routes and Card detail/comment reads are additions for the Project-first demo. Existing Board/Card mutation paths and core request/response fields are reused where possible. Creating a Board under a Project uses the new nested route; the old standalone `POST /api/v1/boards` has no Project association and is not used by this demo. Its long-term compatibility/migration behavior remains a backend decision.

The mock uses the documented bearer-token contract. Current backend controller code still has temporary `X-User-Id` header stubs, so the demo contract is an API design reference and is not directly interchangeable with the current runtime until authentication is wired through.

## Persistence and state lifecycle

```text
Page/component → feature API → Axios (/api/v1) → MSW handler
              → mock repository → JSON response → TanStack Query cache
```

- The mock repository loads a versioned store from localStorage on startup; if none exists, it seeds demo data.
- Mutations write the repository before returning a response. Related TanStack Query entries are invalidated or updated afterward.
- Reload restores Projects, Boards, Cards, Comments, and Notifications from the mock store. Reset replaces product data with the known seed.
- Sign out clears the bearer token and query cache but preserves product data. A new sign-in resolves a fresh user from its token.
- Switching Project changes query keys and closes a Card detail view if its Board is outside the active Project.

This is a single-browser demo store. It does not synchronize changes between browsers or implement the backend WebSocket channel.

## Scope boundaries

- No production authentication, database, email/push delivery, Project rename/delete, invitations, or role administration.
- No Git repositories, branches, commits, pull requests, Workflow, Agents, or Actions.
- No backend consolidation or edits as part of this demo design.
- No cross-browser realtime synchronization; notifications are stored in the local mock repository.

## Acceptance criteria

- The user can sign in, view/create a Project, and view/create Boards within it.
- The user can create a Card, move it across Columns, assign a seeded member in the sample Project, and add/read a comment.
- Notifications can be listed and marked read.
- Product data survives reload and can be reset to the seed; sign-out does not erase it.
- The UI accesses data only through feature API functions and HTTP requests intercepted by MSW.
- Project context is respected when switching Projects and Boards.
- The interface remains usable at desktop and narrow viewport widths.
- Mock API paths, payloads, permission rules, and intentional differences from current backend runtime are documented.

## Implementation verification plan

- Run the frontend linter and production build.
- Exercise the primary browser flow: sign in → open sample Project → create/open Board → create/move Card → assign seeded member/comment → mark Notification read → create another Project → reload → reset seed.
- Check console and network requests for errors and confirm MSW handles the expected `/api/v1` requests.
