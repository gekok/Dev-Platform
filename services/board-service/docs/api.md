# board-service API

`board-service` owns Project, Membership, Invitation, Board and Column in `kanban_board_db`; there is no separate `project-service`. The Project and Membership use cases are described in [Project Service Use Cases](Project_Service_Use_Cases.md). The Board/Column scope is described in [Board and Column Use Cases](Kanban_Board_Service_Use_Cases.md).

## Project and Membership runtime routes

All public routes require an authenticated servlet `Principal` whose name is the account UUID. The service does not accept `X-User-Id` as an identity source. The current Gateway has no JWT authentication filter, so these routes return `401 UNAUTHENTICATED` until trusted authentication populates the Principal.

| Method | Route | Access |
|---|---|---|
| POST | `/api/v1/projects` | Authenticated account creates a Project and its Owner Membership atomically |
| GET | `/api/v1/projects` | Authenticated account lists its ACTIVE memberships; `page`, `size` |
| GET | `/api/v1/projects/{projectId}` | ACTIVE Project member |
| PATCH | `/api/v1/projects/{projectId}` | Project Owner; rename |
| PATCH | `/api/v1/projects/{projectId}/status` | Project Owner; change lifecycle status |
| POST | `/api/v1/projects/{projectId}/invitations` | Owner invites an existing account |
| GET | `/api/v1/projects/{projectId}/invitations` | Owner lists invitations; optional `status`, `page`, `size` |
| POST | `/api/v1/projects/{projectId}/invitations/{invitationId}/cancel` | Owner cancels a pending invitation |
| GET | `/api/v1/invitations/me` | Authenticated user lists own invitations; optional `status`, `page`, `size` |
| POST | `/api/v1/invitations/{invitationId}/accept` | Invitee accepts; creates/reactivates Membership atomically |
| POST | `/api/v1/invitations/{invitationId}/reject` | Invitee rejects |
| GET | `/api/v1/projects/{projectId}/members` | ACTIVE Project member; `page`, `size` |
| DELETE | `/api/v1/projects/{projectId}/members/{userId}` | Owner removes a Member |
| POST | `/api/v1/projects/{projectId}/leave` | Member leaves the Project |
| POST | `/api/v1/projects/{projectId}/transfer-ownership` | Owner transfers ownership to an ACTIVE Member |

Page numbering is zero-based; page size is 1–100. Project resources are hidden with `404 RESOURCE_NOT_FOUND` from non-members; a Member attempting an Owner-only operation receives `403 FORBIDDEN`. Project writes are rejected while status is `COMPLETED` or `CANCELLED`, except reopening the Project to `ACTIVE`.

Responses use `{ "data": ..., "meta": { "correlationId": ..., "timestamp": ... } }`. Errors use the shared `{ "error": { "code", "message", "details" }, "meta": ... }` shape.

## Internal API and integrations

| Method | Route | Purpose |
|---|---|---|
| GET | `/internal/projects/{projectId}/members/{userId}` | Returns Membership role/status, `membershipVersion`, and Project status to an authenticated service |

Internal routes require `X-Service-Token`, compared against `INTERNAL_SERVICE_TOKEN`, and are not configured in Gateway routing. Do not expose the service directly to untrusted clients.

Inviting or accepting an account checks `GET {USER_SERVICE_BASE_URL}/internal/users/{userId}` with the same service token. The User Service endpoint is not implemented yet; until it is available and configured, those flows fail closed with `503 DEPENDENCY_UNAVAILABLE`.

Membership mutations persist the Redis snapshot-sync job and Outbox changes in the same SQL transaction. Redis snapshot sync is disabled by default (`PROJECT_AUTH_SNAPSHOT_SYNC_ENABLED=false`). RabbitMQ relay is also disabled by default, and queue/binding/consumer wiring remains incomplete; a committed Outbox event is not evidence that downstream services have processed it.

## Board runtime status

Public Board and Column APIs require the same authenticated `Principal` as Project routes and do not trust a client-supplied identity header.

| Method | Route | Access |
|---|---|---|
| POST | `/api/v1/projects/{projectId}/boards` | Project Owner; creates a Board and optionally the three default Columns |
| GET | `/api/v1/projects/{projectId}/boards` | ACTIVE Project member; optional `status`, `page`, `size`, `sort` |
| GET | `/api/v1/boards/{boardId}` | ACTIVE Project member; includes sorted Columns, `writable`, `myRole` |
| PATCH | `/api/v1/boards/{boardId}` | Owner; rename only, `boardKey` is immutable |
| PATCH | `/api/v1/boards/{boardId}/status` | Owner; transition Board state |
| POST | `/api/v1/boards/{boardId}/columns` | Owner; add at the end or supplied `position` |
| PATCH | `/api/v1/columns/{columnId}` | Owner; rename Column |
| PUT | `/api/v1/boards/{boardId}/columns/order` | Owner; `columnIds` must list every Board Column exactly once |
| DELETE | `/api/v1/columns/{columnId}` | Owner; only when Task Service reports zero Tasks |

Board listing defaults to `createdAt,desc`, supports `createdAt,asc`, `name,asc`, and `name,desc`, and uses zero-based pages of 1–100 results. Board keys are lowercase letters/digits/hyphens, 2–20 characters, and unique per Project. Column positions are contiguous and writes serialize on the Board row.

`GET /internal/boards/{boardId}` is service-token protected and returns Board/Project status plus Columns for Task Service validation. `GET /internal/columns/{columnId}/task-count` is implemented by Task Service and used by Column deletion. Task card create/move controllers call the Board endpoint before attempting writes. Both internal routes must stay off Gateway public routing. Set `INTERNAL_SERVICE_TOKEN` to the same secret in Board and Task services; set `TASK_SERVICE_BASE_URL` or `BOARD_SERVICE_BASE_URL` if either service is not on its local default URL.

The Task Service's card use-case ports currently lack implementations, so full Task CRUD and live rejection of Task writes on closed Boards cannot yet be verified end-to-end. Column deletion fails closed with `503 DEPENDENCY_UNAVAILABLE` when the count service is unreachable.

## Related documentation

- [Project Service Use Cases](Project_Service_Use_Cases.md)
- [Board and Column Use Cases](Kanban_Board_Service_Use_Cases.md)
- [board_db schema and runtime mapping](database.md)
- [Shared Outbox/Inbox](../../../shared/README.md)
