# Dev Platform Web Client

This React client evolves the Project-first Kỳ 1 Kanban demo into the main web experience while the backend integration is still being completed. It lives in `web-demo/` and does not change the backend services or API Gateway.

## Run locally

Requirements: Node.js 18 or newer and npm.

From this directory:

```sh
npm ci
npm run dev
```

`npm run dev` starts **Demo mode**. Open the local URL printed by Vite. The demo account is:

- Email: `owner@devplatform.demo`
- Password: `kanban-demo`

The separate System Admin demo account is:

- Email: `admin@devplatform.demo`
- Password: `admin-demo`

To start **Backend mode**, run:

```sh
npm run dev:backend
```

Backend mode sends `/api/v1` requests through the Vite proxy to `http://localhost:8080` (the Gateway). Start Gateway and the required backend dependencies first. For another local Gateway, update `VITE_API_PROXY_TARGET` in `.env.backend`. For a deployed build, configure `VITE_API_BASE_URL` to the Gateway or serve the frontend behind a same-origin `/api` reverse proxy, then run `npm run build:backend`.

The checked-in `.env.demo` and `.env.backend` files select the mode explicitly and contain no secrets. Startup/build fails with a configuration error when the mode or API base URL is missing or invalid; the app does not switch modes automatically.

## Demo scope

- Create Projects and Boards, then add and reorder Board Columns.
- Create Cards with a description, due date, and optional Project member assignment.
- Move Cards with drag and drop or the keyboard-accessible Move to control.
- Open Card details, assign a member, add comments, and mention Project members.
- View Notifications, filter unread items, and mark them read.
- Switch between Projects and their Boards.
- Sign in as a System Admin to view the isolated `/admin` console.

The sample Project contains multiple Boards and members for exercising assignment and mentions. A new Project starts with its creator as its only Owner; invitations, member administration, Project rename, and Project deletion are outside this demo scope.

## Data and reset

MSW provides the mock HTTP API in Demo mode, and product data persists in browser localStorage under `dev-platform-demo:db:v1`. The access token is stored separately under `dev-platform-demo:access-token`. Use **Reset demo data** in the top bar and confirm to restore the seeded Project, Boards, Cards, Comments, and Notifications. Reset keeps the current login session. Data is local to one browser and is not synchronized across browsers.

## API boundary

Feature pages call API modules through the shared Axios client. The mode is selected at build/dev startup:

- **Demo mode** starts MSW before rendering the UI. Unmocked `/api/v1/**` calls return an explicit `501 DEMO_API_NOT_MOCKED` response; they do not silently fall through to a backend.
- **Backend mode** does not import, start, or ship the MSW worker. Requests use `VITE_API_BASE_URL`; the local relative `/api/v1` URL is proxied by Vite dev and preview servers to `VITE_API_PROXY_TARGET`. An API failure is displayed as an authentication, permission, network, request, or service error; demo data is not used as a fallback.

Demo credentials, the demo account picker, and the Reset demo data control are loaded only in Demo mode. Backend mode does not initialize the mock repository or show demo credentials; it stores only the signed-in access token in localStorage.

Axios attaches the stored Bearer token. A `401` clears the stored token and the protected route returns to login; `403` remains a permission error. Network and `5xx` failures are normalized and may be retried once by React Query; `4xx` failures are not retried automatically.

The mock uses bearer-token identity and Project membership checks. The backend target and current source are tracked separately in the [shared document index](../docs/README.md), [Project-first API map](../docs/api/kanban-api.md), and [architecture decisions](../docs/project/architecture-decisions.md). The current verification inventory is in [Issue #58 WP1 contract register](docs/issue-58-contract-register.md). Source inspection is not E2E evidence: Project/Membership/Invitation APIs and Project-scoped Board use cases are incomplete; Task Card/Comment operations are fail-closed by default; Notification REST is implemented in source but event generation is not connected end-to-end. Gateway/backend runtime has not been verified from this web-demo.

MSW routes and payloads demonstrate the UI flow; they are not proof that backend controllers or database schemas implement the same contract. Where the demo differs from the approved backend decisions, the shared architecture/API documents are authoritative.

Default Board preferences are Demo-only because no backend service owner or runtime contract has been approved. Backend mode does not request or mutate those preferences; the Board selector explains that limitation. Project-scoped Board list/create APIs also remain a backend dependency and have not been verified end to end.

Backend Card detail/create/move/assignment/comment requests use the Project-scoped Task routes. Card listing is intentionally disabled in Backend mode until the per-Column cursor query, response, ordering, and stale-cursor behavior are approved; Demo/MSW keeps its existing Board-scoped routes. This client-side guard does not verify Task-service runtime readiness.

The Admin console deliberately reuses `POST /api/v1/auth/login` and `GET /api/v1/users/me`. The mock adds a `systemRoles` projection for role-aware navigation, but it does not add an account-management endpoint. `POST /api/v1/admin/accounts` remains a documented proposal and is displayed as unavailable until the backend contract and Gateway enforcement are approved.

Backend target detail: Project creation requires a Gateway-authenticated `ACTIVE` business Account; the actor comes from trusted identity context and Project + Owner membership commit together. `SYSTEM_ADMIN` alone grants no Project access. The Admin role in this client is a demo-only current-user projection; it does not prove Gateway role enforcement or Account snapshot/revocation. Its Board-first mock routes may differ from Project-scoped backend Card routes. See [backend authorization decisions](../docs/project/architecture-decisions.md) and [Kanban API](../docs/api/kanban-api.md).

## Development checks

```sh
npm test
npm run lint
npm run build
```
