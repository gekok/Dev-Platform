# Dev Platform Kanban Demo

This is a standalone React demo for the Project-first Kỳ 1 Kanban flow. It lives in `web-demo/` and does not change the backend services or API Gateway.

## Run locally

Requirements: Node.js 18 or newer and npm.

From this directory:

```sh
npm install
npm run dev
```

Open the local URL printed by Vite. The demo account is:

- Email: `owner@devplatform.demo`
- Password: `kanban-demo`

## Demo scope

- Create Projects and Boards, then add and reorder Board Columns.
- Create Cards with a description, due date, and optional Project member assignment.
- Move Cards with drag and drop or the keyboard-accessible Move to control.
- Open Card details, assign a member, add comments, and mention Project members.
- View Notifications and mark them read.
- Switch between Projects and their Boards.

The sample Project contains multiple Boards and members for exercising assignment and mentions. A new Project starts with its creator as its only Owner; invitations, member administration, Project rename, and Project deletion are outside this demo scope.

## Data and reset

MSW provides the mock HTTP API, and product data persists in browser localStorage under `dev-platform-demo:db:v1`. The access token is stored separately under `dev-platform-demo:access-token`. Use **Reset demo data** in the top bar and confirm to restore the seeded Project, Boards, Cards, Comments, and Notifications. Reset keeps the current login session. Data is local to one browser and is not synchronized across browsers.

## API boundary

Feature pages call API modules through the shared Axios client under `/api/v1`; the browser's MSW worker intercepts those requests. This keeps the UI ready to point at a backend API while the demo remains self-contained.

The mock uses bearer-token identity and Project membership checks. The current backend still has different Kỳ 1 API/auth details, including a temporary `X-User-Id` Board stub and incomplete Card authorization. This demo does not consolidate, replace, or modify those services.

## Development checks

```sh
npm test
npm run lint
npm run build
```
