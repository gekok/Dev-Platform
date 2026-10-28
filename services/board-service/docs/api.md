# board-service API — runtime Board-first và mục tiêu Project-first

**Vai trò mục tiêu:** sở hữu Project, Membership, Invitation, Board và Column trong `board_db`; Kỳ 1 không có deployable `project-service` riêng. Quyết định chung: [architecture decisions](../../../docs/project/architecture-decisions.md); API mục tiêu: [Kanban API](../../../docs/api/kanban-api.md).

## Controller hiện có

`BoardController` hiện ánh xạ các route Board-first dưới đây. Chúng mô tả runtime hiện tại, không phải API đích:

| Method | Route hiện khai báo | Hành vi |
|---|---|---|
| POST | `/api/v1/boards` | Tạo Board |
| GET | `/api/v1/boards` | Liệt kê Board theo User hiện tại |
| GET | `/api/v1/boards/{boardId}` | Lấy Board kèm Columns |
| POST | `/api/v1/boards/{boardId}/columns` | Tạo Column |
| PATCH | `/api/v1/boards/{boardId}/columns/order` | Sắp xếp Column |

Controller hiện đọc `X-User-Id` làm identity stub. Route tạo Board hiện nhận `name`, `key`, tùy chọn `columns`. Controller chưa kiểm tra Project membership vì Project/Membership chưa có model/adapter.

## API mục tiêu đã thống nhất

- Project là ranh giới truy cập; Board thuộc một Project.
- Owner tạo Project và là Owner duy nhất. Owner mời tài khoản hiện có bằng Invitation `PENDING`; Accept mới tạo Membership.
- Owner và Member thao tác Card/Column/comment theo quyền chung; chỉ Owner tạo Board, quản lý member và lifecycle Project/Board.
- Route Project-first nằm trong [kanban-api.md](../../../docs/api/kanban-api.md). Không suy ra payload/error cuối cùng từ route map.
- Board key mục tiêu dùng chữ thường và unique trong Project; không dùng ràng buộc unique toàn DB hiện tại làm contract mới.

## Cascade deletion boundary hiện có

`ProjectDeletionCoordinator` và `ProjectDeletionDataPort` là interface boundary cho việc tìm Project/Board, tạo write barrier, cleanup membership và xóa Project graph. Chưa có implementation adapter, Project entity, consumer hoặc API. Chi tiết hiện trạng: [account deletion skeleton guide](../../../docs/project/account-deletion-skeleton-guide.md).

## Còn mở

Payload và lỗi Invitation/Membership, các route list/reject/cancel/expiry Invitation, status transition của Project/Board, operation status/event response và notification cleanup scope chưa được chốt đầy đủ. Xem mục “Cần bàn tiếp” trong architecture decisions.

## Chi tiết contract được khai báo trong source

Các route runtime dưới đây là Board-first. Header identity đang là stub; chúng chưa thực hiện kiểm tra Membership Project.

| Method | Path | Header/path | Request | Success |
|---|---|---|---|---|
| `POST` | `/api/v1/boards` | `X-User-Id: UUID` bắt buộc | `CreateBoardRequest` | `201`, `BoardResponse` |
| `GET` | `/api/v1/boards` | `X-User-Id: UUID` bắt buộc | Không có body | `200`, `BoardResponse[]` của owner hiện tại |
| `GET` | `/api/v1/boards/{boardId}` | `boardId: Long`, `X-User-Id: UUID` bắt buộc | Không có body | `200`, `BoardResponse` |
| `POST` | `/api/v1/boards/{boardId}/columns` | `boardId: Long`, `X-User-Id: UUID` bắt buộc | `{ "name": "In Review" }` | `201`, `ColumnResponse` |
| `PATCH` | `/api/v1/boards/{boardId}/columns/order` | `boardId: Long`, `X-User-Id: UUID` bắt buộc | `{ "orderedColumnIds": [1, 2, 3] }` | `200`, `ColumnResponse[]` |

### Request/response fields

DTO tạo Board nhận `name`, `key`, `columns`; `key` chỉ được pattern-check nếu có giá trị ở DTO, nhưng domain yêu cầu key hợp lệ. Response Board gồm `id`, `name`, `key`, `ownerId`, `columns`; mỗi Column gồm `id`, `name`, `position`. `BoardAggregate.create` tạo ba cột `To Do`, `In Progress`, `Done` nếu danh sách cột sau khi lọc rỗng, nhưng inbound port hiện chưa có implementation.

| Request field | Kiểu JSON | Bắt buộc/validation và hành vi trong source |
|---|---|---|
| `name` | string | `@NotBlank`; domain trim khoảng trắng đầu/cuối. |
| `key` | string hoặc null | DTO chỉ áp dụng pattern nếu có giá trị; `BoardAggregate.create` vẫn yêu cầu key khớp `[A-Z0-9]{2,20]`. Nếu use case gọi aggregate với key thiếu/null, nó ném `IllegalArgumentException`, được controller handler ánh xạ `400`. |
| `columns` | array[string] hoặc null | Không có validation cho list/phần tử; aggregate bỏ phần tử null/rỗng. Nếu list vắng, rỗng hoặc sau lọc không còn cột, aggregate tạo ba cột mặc định. Inbound port chưa có implementation nên đây là hành vi domain, chưa phải endpoint chạy được. |

```json
{"name":"Platform Roadmap","key":"PLAT","columns":["To Do","In Progress","Done"]}
```

Response shape:

```json
{"id":101,"name":"Platform Roadmap","key":"PLAT","ownerId":"550e8400-e29b-41d4-a716-446655440000","columns":[{"id":1,"name":"To Do","position":0}]}
```

`BoardResponse`: `id` (`Long`), `name`/`key` (string), `ownerId` (UUID), `columns` (array). Mỗi `ColumnResponse` có `id` (`Long`), `name` (string), `position` (integer). Tạo Board trả `201 Created`.

`POST /{boardId}/columns` nhận `name` (string, `@NotBlank`) và trả Column vừa thêm với `201 Created`; `boardId` là `Long`.

`PATCH /{boardId}/columns/order` nhận `orderedColumnIds` (array số nguyên Long, `@NotEmpty`); domain kiểm tra danh sách đủ ID hiện có, không trùng và chỉ chứa Column của Board. Response là mảng `ColumnResponse`, status `200 OK`. Giá trị null bên trong list không có constraint DTO riêng và có thể bị domain từ chối.

### Identity, lỗi và readiness

| Tình huống được handler bắt | HTTP | Code |
|---|---:|---|
| Bean Validation hoặc `IllegalArgumentException` | `400` | `BAD_REQUEST` |
| Không tìm thấy Board/Column (`NoSuchElementException`) | `404` | `BOARD_NOT_FOUND` |
| `IllegalStateException` khi trùng key | `409` | `BOARD_KEY_CONFLICT` |

Body lỗi có `code`, `message`, `timestamp`, `path`. Lỗi header thiếu/UUID sai không có handler riêng. `X-User-Id` khi tạo/lấy danh sách/chi tiết được dùng làm UUID owner; ở add/reorder controller chỉ kiểm tra có header rồi bỏ UUID, không truyền actor xuống use case. Không có kiểm tra owner/membership ở API boundary.

Controller yêu cầu `CreateBoardPort`, `AddColumnPort`, `ReorderColumnsPort`, `GetBoardPort`; source chỉ có persistence adapter cho outbound `SaveBoardPort`, chưa có các inbound implementation trên. Các bảng route do đó là mapping source, không khẳng định endpoint hiện khởi chạy được.

## API mục tiêu thuộc board-service

Board-service là owner của Project, Membership, Invitation, Board và Column trong `board_db`. Bản API mục tiêu (chưa phải controller runtime):

| Method | Route | Request/Response | Quyền/ghi chú |
|---|---|---|---|
| `GET`, `POST` | `/api/v1/projects` | Create `{name}` → `201 ProjectSummary`; list → `200 ProjectSummary[]` | Project creation chỉ dành cho Account nghiệp vụ; System Admin không được mặc định làm Owner (đa vai trò còn mở) |
| `GET` | `/api/v1/projects/{projectId}` | `200 ProjectDetail` | Chỉ Member/Owner |
| `GET` | `/api/v1/projects/{projectId}/members` | `200 MemberSummary[]` | Chỉ Member/Owner; Account fields lấy bằng UUID reference |
| `POST` | `/api/v1/projects/{projectId}/invitations` | `{userId}` → `201 Invitation(PENDING)` | Owner; chỉ user đã có tài khoản; không tạo membership |
| `GET` | `/api/v1/projects/{projectId}/invitations?status=PENDING` | `200 Invitation[]` (đề xuất) | Owner xem các lời mời để theo dõi/hủy |
| `GET` | `/api/v1/users/me/project-invitations` | `200 Invitation[]` | Route đề xuất, chỉ recipient |
| `POST` | `/api/v1/projects/{projectId}/invitations/{inviteId}/accept` | Tạo Membership atomically; `201 Membership` là response đề xuất | Chỉ recipient; không trả body cùng `204` |
| `POST` | `/api/v1/projects/{projectId}/invitations/{inviteId}/reject` | `204 No Content` đề xuất | Chỉ recipient; route đề xuất |
| `DELETE` | `/api/v1/projects/{projectId}/invitations/{inviteId}` | `204` đề xuất | Owner hủy PENDING; route đề xuất |
| `DELETE` | `/api/v1/projects/{projectId}/members/{userId}` | `204` không body sau khi revoke hiệu lực + Outbox commit | Owner gỡ Member; Member chỉ rời chính mình; Owner không tự gỡ |
| `GET`, `POST` | `/api/v1/projects/{projectId}/boards` | Create `{name,key,columns?}` → `201`; list → `200` | Tạo Board chỉ Owner |
| `GET` | `/api/v1/boards/{boardId}` | `200 BoardDetail(columns[])` | Member/Owner |
| `POST` | `/api/v1/boards/{boardId}/columns` | `{name}` → `201 Column` | Owner và Member cùng được tạo Column |
| `PATCH` | `/api/v1/boards/{boardId}/columns/order` | `{orderedColumnIds}` → `200 Column[]` | Quyền reorder còn mở |

`ProjectSummary`, `ProjectDetail`, `Invitation`, `Board` và error payloads có field proposal tại [contract chung](../../../docs/api/kanban-api.md#2-contract-chi-tiết-project-first-mục-tiêu-bản-thiết-kế). API status Project/Board chưa thêm vì transition graph chưa chốt. Invitation PENDING không cấp quyền; chỉ Accept tạo `MEMBER`.

Request mời dùng `{ "userId": "uuid" }`; role không do client chọn. `Invitation` chỉ có hiệu lực PENDING và không cấp quyền. Owner list lời mời theo Project qua GET invitations (filter `status` là đề xuất) để theo dõi/cancel; chỉ Owner được gọi. Accept do đúng recipient gọi; khi thành công tạo Membership `MEMBER` trong cùng transaction, response `201 Membership` là đề xuất. Route reject dùng cùng resource path với accept; chỉ recipient được reject. Owner chỉ cancel Invitation PENDING.

User directory search chỉ giúp client chọn ID, không chứng minh User còn tồn tại/ACTIVE. Board-service phải tự validate `userId` lúc tạo Invitation và revalidate lúc Accept để tránh account bị xóa/deactivated trong lúc Invitation đang PENDING. Vì Internal REST chưa được bảo vệ/triển khai, nguồn check (versioned Redis snapshot hay cơ chế khác) là blocker contract; không bật route chỉ dựa vào UUID client gửi.

Khi gỡ Member, board-service thu hồi Membership trước và ghi Outbox cùng transaction. API chỉ trả thành công khi authorization gate không còn chấp nhận snapshot cũ; task-service unassign Card ở nền, không làm chậm việc revoke, và Comment/history được giữ. Redis invalidation/fencing là cơ chế cần chốt; RabbitMQ delivery bất đồng bộ đơn thuần không bảo đảm request tiếp theo bị chặn. Nếu cleanup cũ còn pending rồi user được mời/accept lại, cleanup cũ không được xóa assignment mới; cần chốt khóa rejoin đến khi cleanup hoàn tất hoặc generation/cutoff.

Mã lỗi gợi ý, chưa duyệt: `404 USER_NOT_FOUND` / `INVITATION_NOT_FOUND`; `409 USER_NOT_ACTIVE` / `MEMBER_ALREADY_EXISTS` / `INVITATION_ALREADY_PENDING` / `INVITATION_NOT_PENDING` / `PROJECT_NOT_ACTIVE` / `OWNER_CANNOT_LEAVE_PROJECT`; `403 PROJECT_ACCESS_DENIED` / `PROJECT_OWNER_REQUIRED`. User search để Owner lấy `userId` thuộc user-service; API directory và profile composition được ghi trong [contract chung](../../../docs/api/kanban-api.md#2-contract-chi-tiết-project-first-mục-tiêu-bản-thiết-kế).
