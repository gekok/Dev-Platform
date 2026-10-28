# Kanban API Kỳ 1 — trạng thái chuyển sang Project-first

**Trạng thái:** Hướng resource Project-first và các nghiệp vụ chính đã thống nhất. Đây chưa phải OpenAPI/schema cuối cùng: payload Invitation, một số mã lỗi, status operation, query cursor và event contract còn mở trong [architecture decisions](../project/architecture-decisions.md).

**Base URL mục tiêu qua Gateway:** `http://localhost:8080/api/v1`.

**Runtime hiện tại:** backend vẫn chủ yếu dùng Board-first API; Auth/Gateway cũng chưa được nối hoàn chỉnh (login port chưa có implementation, `/users/me` còn dùng `X-User-Id`). Phần Board-first ở cuối tài liệu được giữ làm tham chiếu cho code/tài liệu cũ, không phải contract để phát triển tính năng Project-first mới.

## 1. Route map mục tiêu Project-first

Các route dưới đây ghi lại phạm vi đã thống nhất và route đang được dùng làm tham chiếu trong React/MSW demo. Payload, response, lỗi và mức độ đã implement không được suy ra chỉ từ route map này.

| Nhóm | Method | Endpoint | Hành vi mục tiêu |
|---|---|---|---|
| Auth | `POST` | `/api/v1/auth/login` | Đăng nhập theo contract hiện có |
| User | `GET` | `/api/v1/users/me` | Lấy tài khoản hiện tại |
| User directory | `GET` | `/api/v1/users?query={text}&limit=20` | Tìm tài khoản đang hoạt động để Owner chọn mời; route đề xuất |
| User profile | `GET` | `/api/v1/users/profiles?ids={uuid,...}` | Lấy profile/tombstone tối thiểu cho Member và lịch sử; route đề xuất |
| Project | `GET`, `POST` | `/api/v1/projects` | Liệt kê và tạo Project |
| Project | `GET` | `/api/v1/projects/{projectId}` | Xem Project |
| Membership | `GET` | `/api/v1/projects/{projectId}/members` | Liệt kê Member chính thức |
| Invitation | `POST` | `/api/v1/projects/{projectId}/invitations` | Owner mời tài khoản đã tồn tại; trạng thái ban đầu `PENDING` |
| Invitation | `GET` | `/api/v1/projects/{projectId}/invitations?status=PENDING` | Owner xem các lời mời của Project để theo dõi/hủy; route đề xuất |
| Invitation | `GET` | `/api/v1/users/me/project-invitations` | Danh sách lời mời gửi tới tài khoản hiện tại; route đề xuất |
| Invitation | `POST` | `/api/v1/projects/{projectId}/invitations/{inviteId}/accept` | Người được mời chấp nhận; lúc này mới tạo Membership |
| Invitation | `POST` | `/api/v1/projects/{projectId}/invitations/{inviteId}/reject` | Người được mời từ chối; route đề xuất |
| Invitation | `DELETE` | `/api/v1/projects/{projectId}/invitations/{inviteId}` | Owner hủy lời mời; route đề xuất |
| Membership | `DELETE` | `/api/v1/projects/{projectId}/members/{userId}` | Owner gỡ Member hoặc Member tự rời Project; Owner duy nhất không thể tự gỡ |
| Board | `GET`, `POST` | `/api/v1/projects/{projectId}/boards` | Liệt kê và tạo Board trong Project |
| Board | `GET` | `/api/v1/boards/{boardId}` | Xem Board và Columns |
| Column | `POST` | `/api/v1/boards/{boardId}/columns` | Tạo Column |
| Column | `PATCH` | `/api/v1/boards/{boardId}/columns/order` | Sắp xếp lại Columns |
| Card | `GET`, `POST` | `/api/v1/boards/{boardId}/cards` | Liệt kê và tạo Card |
| Card | `GET` | `/api/v1/cards/{cardId}` | Xem Card |
| Card | `PATCH` | `/api/v1/cards/{cardId}/move` | Chuyển Card |
| Card | `PATCH` | `/api/v1/cards/{cardId}/assignee` | Gán người xử lý |
| Comment | `GET`, `POST` | `/api/v1/cards/{cardId}/comments` | Xem và tạo Comment |
| Notification | `GET` | `/api/v1/notifications/me` | Liệt kê Notification của tài khoản hiện tại |
| Notification | `PATCH` | `/api/v1/notifications/{notificationId}/read` | Đánh dấu đã đọc |
| Account Admin | `POST` | `/api/v1/admin/accounts` | Tạo Account hệ thống; route/payload đề xuất, chờ chốt |
| Account Admin | `DELETE` | `/api/v1/admin/accounts/{accountId}` | Khởi tạo cascade xóa Account, trả `202` + `operationId`; route đề xuất |
| Account Admin | `GET` | `/api/v1/admin/deletion-operations/{operationId}` | Đọc tiến độ cascade mà `statusUrl` trả về; route/response đề xuất |
| Board preference | `GET` | `/api/v1/users/me/preferences/default-boards` | Route tham khảo từ demo; ownership/storage/access validation backend chưa chốt |
| Board preference | `PUT`, `DELETE` | `/api/v1/users/me/preferences/default-boards/{projectId}` | Route tham khảo từ demo; service sở hữu và behavior khi Project/Board bị xóa chưa chốt |

### Quy tắc đã thống nhất và phần còn mở

- Project là ranh giới membership; Board thuộc một Project. `Project` và `Board` dùng ID số; User dùng UUID.
- Owner và Member có quyền thao tác công việc như nhau. Owner có thêm quyền tạo Board, quản lý trạng thái Project/Board và membership.
- Kỳ 1 chỉ thêm tài khoản đã có trong hệ thống bằng Invitation; người nhận chấp nhận mới trở thành Member. Mời người ngoài hệ thống và đổi vai trò để Kỳ 2.
- Owner cần tra cứu tài khoản đang hoạt động để chọn người mời; API directory/bulk profile dưới đây là đề xuất bổ sung, chưa có route runtime.
- Project và Board dùng `ACTIVE`, `COMPLETED`, `CANCELLED`; transition cụ thể và route cập nhật trạng thái chưa chốt.
- API list Card dùng cursor: mặc định 5, tối đa 20, `position DESC, id DESC`, cursor opaque gồm `position` và `id`. Cặp `(position,id)` đã tạo thứ tự toàn phần vì `id` là duy nhất. Wire proposal cho infinite-scroll riêng từng Column yêu cầu `columnId` và cursor ràng buộc với `boardId` + `columnId` + sort; đây là scope API/UI cần duyệt, không phải điều kiện toán học của cursor.
- Gateway là ranh giới xác thực mục tiêu. Internal REST từ `task-service` sang `board-service` để kiểm tra quyền chưa triển khai cho tới khi bảo mật giao tiếp nội bộ được giải quyết.
- Route/payload Admin tạo Account, API directory/bulk profile, status operation, lỗi nghiệp vụ Membership/Invitation, policy list/reject/cancel/expiry Invitation, transition Project/Board, status Card, response envelope/cursor scope, retry/DLQ, phạm vi Notification cascade và service/storage/access validation của Board preference còn mở hoặc mới ở mức đề xuất.

Các route preference và nhiều route Kanban bắt nguồn từ MSW demo; Invitation/Accept và quyền Project-first là quyết định backend sau đó, không phải endpoint đã có trong demo. Xem [chỉ mục tài liệu](../README.md) và [architecture decisions](../project/architecture-decisions.md) để biết thứ tự ưu tiên.

## 2. Contract chi tiết Project-first mục tiêu (bản thiết kế)

Phần này bổ sung payload/response và quyền theo service để làm nền cho implementation. Đây là **bản thiết kế**, không phải OpenAPI đã duyệt hoặc bằng chứng backend đã chạy. Mỗi điểm được ghi `Đã chốt`, `Đề xuất` hoặc `Còn mở`; khi sửa một mục đang mở, cập nhật architecture decisions và các tài liệu service cùng lúc.

### Quy ước chung

- Client gọi qua Gateway và gửi `Authorization: Bearer <accessToken>`. `userId` của người gọi được suy ra từ danh tính đã xác thực; không nhận `userId` do client gửi để giả danh người gọi. Gateway hiện chưa hoàn thiện Auth/authorization.
- Project, Board, Column, Card, Comment, Notification dùng ID số; Account dùng UUID. ID giữa database/service chỉ là tham chiếu logic, không tạo FK chéo database.
- Lỗi mục tiêu dùng envelope `{ "code", "message", "timestamp", "path" }` theo format đang được tham chiếu trong tài liệu. Mã lỗi cụ thể phải ổn định theo từng nghiệp vụ; mapping nào chưa chốt được đánh dấu ở dưới.
- Thành công ghi dữ liệu đồng bộ chỉ được trả sau khi business data và Outbox cùng commit. Khi gỡ Member, board-service thu hồi Membership trước; quyền phải bị chặn hiệu lực trên mọi request mới trước khi API báo thành công. Task unassign chạy nền, response không chờ worker. Nếu Gateway đọc snapshot, Redis invalidation/fencing là yêu cầu cần thiết; RabbitMQ bất đồng bộ một mình không bảo đảm thu hồi tức thời. Cơ chế cụ thể và điểm xác nhận còn mở; khi không chứng minh được quyền hiện hành, phải fail closed.
- Mapping HTTP khung đề xuất: `401` chưa xác thực; `403` không có quyền; `404` resource không tồn tại/không thuộc phạm vi đọc; `400` input sai; `409` xung đột trạng thái/nghiệp vụ; `503 AUTHORIZATION_UNAVAILABLE` khi không thể chứng minh revoke/fence đã có hiệu lực và phải fail closed. Status/error code cụ thể vẫn cần chốt.

### `user-service`: Auth và System Admin

| Method | Route mục tiêu | Request | Thành công | Quyền/trạng thái |
|---|---|---|---|---|
| `POST` | `/api/v1/auth/login` | `{ "email": "...", "password": "..." }` | `200 { "accessToken": "...", "tokenType": "Bearer" }` | Account `DELETING`/đã xóa không được cấp token; token claims/expiry còn mở |
| `GET` | `/api/v1/users/me` | Không body | `200 { "id": "uuid", "email": "...", "fullName": "..." }` | Chỉ Account hiện tại |
| `GET` | `/api/v1/users?query={text}&limit=20` | Query tìm theo tên/email | `200 UserCandidate[]` (đề xuất) | User đã xác thực; chỉ trả Account đang hoạt động; privacy/limit còn mở |
| `GET` | `/api/v1/users/profiles?ids={uuid,...}` | UUID batch trong query | `200 UserProfile[]` (đề xuất) | User đã xác thực; field tối thiểu phục vụ tên tác giả/member và tombstone |
| `POST` | `/api/v1/admin/accounts` | Payload tạo Account **còn mở** | `201 AccountSummary` (đề xuất) | Chỉ System Admin; route/status/payload còn mở |
| `DELETE` | `/api/v1/admin/accounts/{accountId}` | Không body | `202 { "operationId": "uuid", "status": "PENDING", "statusUrl": "..." }` | Chỉ System Admin; lưu operation + Outbox và chặn auth Account trước response |
| `GET` | `/api/v1/admin/deletion-operations/{operationId}` | Path UUID | `200 DeletionOperation` (đề xuất) | Chỉ System Admin; route/response/error còn mở |

`UserCandidate` đề xuất chỉ gồm `id`, `fullName`, `email`; `UserProfile` đề xuất gồm `id`, `displayName`, `accountDeleted`. Email chỉ trả trong luồng chọn người mời nếu quyền directory được duyệt. Bulk profile trả dấu tombstone cho UUID đã xóa để UI giữ attribution. “Không còn trong Project” được xác định bằng đối chiếu với Membership hiện tại do board-service sở hữu. Cách client compose hai API, quyền tra cứu directory, query syntax và giới hạn batch còn mở.

`202` chỉ xác nhận flow đã được ghi bền vững và Account không còn được authorization path chấp nhận; không xác nhận service downstream đã hoàn tất. Nếu snapshot chưa thể bị fence, fail closed và không báo thành công. Ví dụ status operation `PENDING`, `PROCESSING`, `RETRYING`, `FAILED`, `COMPLETED` là đề xuất; state machine, retry visibility và response fields chưa được duyệt. Xóa Account không xóa Project mà Account chỉ là Member; Membership ở các Project đó bị thu hồi trước, rồi Card assignment được dọn nền. Project thuộc Owner được cascade theo `projectDeletionId`.

`DeletionOperation` đề xuất trả `operationId`, `status`, `createdAt`, `updatedAt`, `totalProjects`, `completedProjects`, `pendingProjects`, `failedProjects` và các lỗi/bước cần retry đã được lọc an toàn. `statusUrl` từ DELETE phải trỏ đúng GET theo `operationId`; response fields, quyền đọc khi Account đã xóa và cách phân trang lỗi còn mở.

### `board-service`: Project, Membership, Invitation, Board, Column

| Method | Route mục tiêu | Request/Query | Thành công | Quyền chính |
|---|---|---|---|---|
| `GET` | `/api/v1/projects` | Không body | `200 ProjectSummary[]` | Member/Owner thấy Project mình tham gia |
| `POST` | `/api/v1/projects` | `{ "name": "MSS Platform" }` | `201 ProjectSummary`; người tạo thành Owner | Account nghiệp vụ đã xác thực; System Admin không có quyền Project (quy tắc account đa vai trò còn mở) |
| `GET` | `/api/v1/projects/{projectId}` | Path ID | `200 ProjectDetail` | Member/Owner |
| `GET` | `/api/v1/projects/{projectId}/members` | Path ID | `200 MemberSummary[]` | Member/Owner |
| `POST` | `/api/v1/projects/{projectId}/invitations` | `{ "userId": "uuid" }` | `201 Invitation` có `status: PENDING` | Owner; tài khoản đã tồn tại |
| `GET` | `/api/v1/projects/{projectId}/invitations?status=PENDING` | `status` tùy chọn | `200 Invitation[]` (đề xuất) | Owner xem lời mời của Project để theo dõi/hủy |
| `GET` | `/api/v1/users/me/project-invitations` | Không body | `200 Invitation[]` | Người nhận hiện tại; route đề xuất |
| `POST` | `/api/v1/projects/{projectId}/invitations/{inviteId}/accept` | Không body | `201 Membership` (đề xuất; không gắn body với `204`) | Chỉ người nhận; accept + tạo Membership cùng transaction |
| `POST` | `/api/v1/projects/{projectId}/invitations/{inviteId}/reject` | Không body | `204 No Content` (đề xuất) | Chỉ người nhận; route đề xuất |
| `DELETE` | `/api/v1/projects/{projectId}/invitations/{inviteId}` | Không body | `204` (đề xuất) | Owner hủy Invitation; route đề xuất |
| `DELETE` | `/api/v1/projects/{projectId}/members/{userId}` | Path IDs | `204` không body sau khi Membership bị revoke hiệu lực + Outbox commit | Owner gỡ Member; Member chỉ tự rời; Owner không tự gỡ |
| `GET`, `POST` | `/api/v1/projects/{projectId}/boards` | Create: `{ "name": "...", "key": "ops", "columns": ["To Do"] }` | `200 BoardSummary[]` / `201 BoardDetail` | List Member/Owner; tạo Board chỉ Owner |
| `GET` | `/api/v1/boards/{boardId}` | Path ID | `200 BoardDetail` kèm Columns | Member/Owner |
| `POST` | `/api/v1/boards/{boardId}/columns` | `{ "name": "Review" }` | `201 Column` | Owner và Member theo quyền đã chốt |
| `PATCH` | `/api/v1/boards/{boardId}/columns/order` | `{ "orderedColumnIds": [1, 2, 3] }` | `200 Column[]` | Quyền reorder còn mở |

Payload/response field tham khảo:

```json
{
  "id": 101,
  "name": "MSS Platform",
  "ownerId": "550e8400-e29b-41d4-a716-446655440000",
  "role": "OWNER",
  "status": "ACTIVE",
  "createdAt": "2026-10-28T09:00:00Z"
}
```

`ProjectSummary` có thể thêm `boardCount`; Board có `projectId`, `name`, `key`, `status`, `ownerId`; Column có `id`, `name`, `position`. Đây là shapes từ demo/thiết kế; field `createdAt`, `boardCount`, `ownerId` duplication và nullability cần thống nhất trước OpenAPI. `Board.key` target dùng chữ thường, unique theo Project; mock demo hiện dùng uppercase/global unique nên không phải contract target.

Request mời đã chốt dùng `userId` của Account có sẵn, không dùng email người ngoài hệ thống ở Kỳ 1:

```json
{ "userId": "550e8400-e29b-41d4-a716-446655440000" }
```

`Invitation` response đề xuất gồm `id`, `projectId`, `userId`, `invitedByUserId`, `status`, `createdAt`; `expiresAt` chỉ thêm khi expiry policy được chốt. Accept tạo `Membership` với `userId`, `projectId`, `role: MEMBER`, `joinedAt`; response đề xuất `201 Created` với representation đó. `MemberSummary` đề xuất gồm `userId`, `role`, `joinedAt`; profile được lấy qua API user directory/bulk-profile hoặc composition khác còn mở.

Invitation `PENDING` không tạo Membership/quyền truy cập. Accept chỉ do người nhận gọi và tạo Membership trong transaction board-service. Client không chọn role; người nhận trở thành `MEMBER`. Không cho pending trùng cùng User+Project (409 đề xuất). Board-service phải kiểm tra User tồn tại/ACTIVE khi tạo Invitation và revalidate khi Accept; user directory do client gọi không thay thế check server-side. Vì Internal REST chưa sẵn sàng, nguồn kiểm tra/freshness Account còn mở. Mã lỗi `USER_NOT_FOUND`, `USER_NOT_ACTIVE`, `MEMBER_ALREADY_EXISTS`, `INVITATION_NOT_FOUND`, `INVITATION_NOT_PENDING`, `PROJECT_NOT_ACTIVE` còn cần chốt.

Gỡ Member: board-service thu hồi Membership trước, lưu assignment-cleanup operation + Outbox trong cùng transaction. API chỉ trả `204` sau khi quyền bị chặn hiệu lực cho request mới; Task-service nhận event và unassign Card thuộc `boardIds` ở nền; Comment/history được giữ. `DELETE` không chờ assignment cleanup. Redis invalidation/fencing và cách xử lý khi chưa xác nhận được revoke còn mở. Nếu cleanup cũ còn pending rồi Member được mời/accept lại, event cũ không được xóa assignment mới; cần chốt khóa rejoin đến khi cleanup xong hoặc generation/cutoff cho assignment.

### Quy tắc lỗi mục tiêu (mã cụ thể đang là đề xuất)

| Tình huống | HTTP đề xuất | Error code đề xuất |
|---|---:|---|
| Project/Invitation/Member không tồn tại hoặc không thuộc phạm vi đọc | `404` | `PROJECT_NOT_FOUND`, `INVITATION_NOT_FOUND`, `MEMBER_NOT_FOUND` |
| Người gọi không phải Member/Owner, hoặc không phải Owner khi thao tác quản trị | `403` | `PROJECT_ACCESS_DENIED`, `PROJECT_OWNER_REQUIRED` |
| User ID mời không tồn tại | `404` | `USER_NOT_FOUND` |
| User đã bị xóa/đang `DELETING` | `409` | `USER_NOT_ACTIVE` |
| User đã là Member hoặc đã có Invitation PENDING | `409` | `MEMBER_ALREADY_EXISTS`, `INVITATION_ALREADY_PENDING` |
| Invitation không còn PENDING / Project không ACTIVE | `409` | `INVITATION_NOT_PENDING`, `PROJECT_NOT_ACTIVE` |
| Owner cố tự gỡ mình | `409` | `OWNER_CANNOT_LEAVE_PROJECT` |

Các mã trên là gợi ý để UI/API cùng xử lý; chưa phải error contract đã duyệt. Request ngoài Gateway không có identity tin cậy; không lấy `userId` trong payload làm actor.

Project/Board status dùng `ACTIVE`, `COMPLETED`, `CANCELLED`, nhưng chưa có route/status transition được chốt. Chưa thêm `PATCH /status` vào route hợp đồng. Khi transition được duyệt, bổ sung route và ma trận thao tác đọc/ghi.

### `task-service`: Card, assignment, Comment

| Method | Route mục tiêu | Request/Query | Thành công | Quyền chính |
|---|---|---|---|---|
| `GET` | `/api/v1/boards/{boardId}/cards?columnId={columnId}&limit=5&cursor={opaque}` | Per-Column pagination proposal: `columnId` bắt buộc; `limit` mặc định 5, tối đa 20; `cursor` tùy chọn | `200 CardPage` | Member/Owner; cursor sort `position DESC, id DESC`, gắn với Board + Column |
| `POST` | `/api/v1/boards/{boardId}/cards` | `{ "columnId": 10, "title": "...", "description": null, "assigneeId": null, "dueDate": null }` | `201 Card` | Member/Owner; Column thuộc Board; assignee thuộc Project |
| `GET` | `/api/v1/cards/{cardId}` | Path ID | `200 CardDetail` | Member/Owner |
| `PATCH` | `/api/v1/cards/{cardId}/move` | `{ "toColumnId": 11, "toPosition": 0 }` | `200 Card` | Member/Owner; Column đích cùng Board |
| `PATCH` | `/api/v1/cards/{cardId}/assignee` | `{ "assigneeId": "uuid" }`; `null` để bỏ gán | `200 Card` | Member/Owner; assignee phải là Project Member |
| `GET` | `/api/v1/cards/{cardId}/comments` | Path ID | `200 Comment[]` | Member/Owner |
| `POST` | `/api/v1/cards/{cardId}/comments` | `{ "content": "...", "mentionedUserIds": ["uuid"] }` | `201 Comment` | Member/Owner; mentioned users là Project Member |

Envelope phân trang đề xuất:

```json
{
  "items": [{ "id": 42, "boardId": 7, "columnId": 10, "title": "Prepare release", "position": 8, "assigneeId": null }],
  "nextCursor": "opaque-token-or-null",
  "hasMore": true
}
```

Thứ tự đã chốt là giảm dần theo `(position, id)`. `id` unique nên cặp này total order kể cả khi position lặp giữa Column. Vì UI tải vô hạn riêng từng Column, wire proposal cho endpoint trên yêu cầu `columnId`; cursor phải chỉ dùng lại với cùng `boardId`, `columnId` và sort contract, nếu sai phạm vi thì trả `400 INVALID_CURSOR` (đề xuất). Token opaque có thể chứa hoặc ký scope cùng `{position,id}`. Tên query, shape `items/nextCursor/hasMore`, lỗi cursor và snapshot consistency vẫn cần duyệt. `columnId` là scope API/UI, không phải điều kiện toán học để cursor xác định thứ tự. Backend đọc trạng thái hiện hành mỗi request, không hứa snapshot pagination. Client dedupe `cardId`, dùng realtime nếu có và hiện banner đầu bảng yêu cầu reload khi phát hiện bất nhất.

Card response tối thiểu đề xuất có `id`, `boardId`, `columnId`, `title`, `position`, `assigneeId`, cùng `description`/`dueDate` ở detail. `status` hiện có trong demo (`TODO`, `IN_PROGRESS`, `DONE`) nhưng không đổi theo move; cần chốt có bỏ field này hay ràng buộc nó với Column. Comment giữ `id`, `cardId`, `authorId`, `content`, `createdAt`; `mentionedUserIds` là request input, response/storage model cần thống nhất.

### `notification-service`: Notification

| Method | Route mục tiêu | Request/Query | Thành công | Quyền |
|---|---|---|---|---|
| `GET` | `/api/v1/notifications/me?unreadOnly=false` | `unreadOnly` mặc định false | `200 Notification[]` | Chỉ Notification của Account hiện tại |
| `PATCH` | `/api/v1/notifications/{notificationId}/read` | Không body | `200 Notification` đã đọc | Chỉ recipient |

Shape tham khảo: `{ id, type, title, cardId, boardId, read, createdAt }`. Demo hiện dùng `ASSIGNED`, `MENTIONED`, `MOVED`; type Kỳ 1 cần duyệt theo event thực tế. Notification được tạo từ domain event/internal integration, không cho client tự tạo. API internal runtime hiện tại chưa có bảo vệ service-to-service; không expose qua Gateway.

Khi cascade Project, notification-service chỉ xóa dữ liệu do nó sở hữu. Phạm vi Notification gắn Board và Notification không gắn Board phải được chốt; event request/completion nội bộ không phải HTTP client API. Cần deletion fence/tombstone hoặc version rule để event Notification đến muộn không tạo lại dữ liệu của Board đã xóa; Inbox chỉ chống lặp cùng eventId.

### Board preference (chỉ từ demo, chưa phải service contract)

MSW demo có `GET /api/v1/users/me/preferences/default-boards`, `PUT /api/v1/users/me/preferences/default-boards/{projectId}` với `{ "boardId": 1 }`, và `DELETE` cùng path (có query `expectedBoardId`). Service sở hữu, DB, kiểm tra Member/Board còn hoạt động và cleanup khi Project/Board bị xóa đều chưa chốt; không coi các route này là API backend đã duyệt.

### Dữ liệu thay đổi khi đang cuộn

Backend đọc trạng thái hiện hành cho từng request. `position` đổi khi Card di chuyển; do đó dùng keyset cursor, không dùng offset/page-number. Inbox/event dedupe không giải quyết pagination. Khi realtime hoặc phản hồi phát hiện bất nhất, UI phải hiện banner đầu bảng yêu cầu reload thay vì âm thầm gộp danh sách sai. Cách phát hiện stale cursor và HTTP error cụ thể còn mở.

### Độ sẵn sàng

Các route trên là target design; Board-first controllers hiện tại không cung cấp đầy đủ route Project-first. Auth/Gateway, Project/Membership entities, cursor API, invitation API, permission checks, Redis authorization snapshot và cleanup workers chưa hoàn chỉnh. Xem tài liệu API/schema riêng của service để đối chiếu source.

## 3. Hợp đồng Board-first cũ — chỉ tham khảo runtime

Phần dưới mô tả API Board-first được viết trước quyết định Project-first. Một số controller đang dùng các route này, nhưng nội dung cũ không đồng nghĩa mọi endpoint đã hoạt động hoàn chỉnh. Không dùng phần này làm thiết kế đích cho API mới.

Base URL cũ qua Gateway:

```text
http://localhost:8080/api/v1
```

Contract cũ mô tả JWT `sub` làm danh tính và database riêng cho từng service. Thực tế một số controller hiện còn đọc `X-User-Id`; xem ghi chú runtime ở [README](../../README.md).

| Service | Port | DB |
|---|---|---|
| gateway | 8080 | — |
| user-service | 8081 | user_db |
| board-service | 8082 | board_db |
| task-service | 8083 | task_db |
| notification-service | 8084 | notification_db |

### Auth / User (contract cũ)

Thuộc `user-service`. Cấp vé JWT cho các service khác tin.

#### Đăng ký

| Method | Endpoint |
|---|---|
| `POST` | `/api/v1/auth/register` |

Request:

```json
{
  "email": "sv@fpt.edu.vn",
  "password": "123456",
  "fullName": "Nguyen Van A"
}
```

Response `201 Created`:

```json
{
  "id": "c0a8b1e2-0000-4000-8000-000000000001",
  "email": "sv@fpt.edu.vn",
  "fullName": "Nguyen Van A"
}
```

#### Đăng nhập

| Method | Endpoint |
|---|---|
| `POST` | `/api/v1/auth/login` |

Request:

```json
{
  "email": "sv@fpt.edu.vn",
  "password": "123456"
}
```

Response `200 OK`:

```json
{
  "accessToken": "<jwt>",
  "tokenType": "Bearer"
}
```

#### Lấy hồ sơ hiện tại

| Method | Endpoint |
|---|---|
| `GET` | `/api/v1/users/me` |

Header: `Authorization: Bearer <jwt>`.

Response `200 OK`: trả về `id`, `email`, `fullName` của `sub` trong token.

### Board

Thuộc `board-service`. Giữ bảng và cột. Không giữ thẻ chi tiết.

#### Tạo bảng

| Method | Endpoint |
|---|---|
| `POST` | `/api/v1/boards` |

Request:

```json
{
  "name": "MSS301 Project",
  "key": "MSS",
  "columns": ["To Do", "In Progress", "Done"]
}
```

Nếu không gửi `columns`, server tự tạo 3 cột mặc định trên.

Response `201 Created`:

```json
{
  "id": 1,
  "name": "MSS301 Project",
  "key": "MSS",
  "ownerId": "c0a8b1e2-0000-4000-8000-000000000001",
  "columns": [
    { "id": 10, "name": "To Do", "position": 0 },
    { "id": 11, "name": "In Progress", "position": 1 },
    { "id": 12, "name": "Done", "position": 2 }
  ]
}
```

#### Lấy danh sách bảng của tôi

| Method | Endpoint |
|---|---|
| `GET` | `/api/v1/boards` |

Response `200 OK`: mảng bảng mà `sub` là owner hoặc member.

#### Lấy chi tiết bảng kèm cột

| Method | Endpoint |
|---|---|
| `GET` | `/api/v1/boards/{boardId}` |

Response `200 OK`: gồm thông tin Board và Columns như response của
`POST /api/v1/boards`; không trả Card ở đây. Card lấy ở Task API để Board
không phình to.

#### Thêm cột

| Method | Endpoint |
|---|---|
| `POST` | `/api/v1/boards/{boardId}/columns` |

Request:

```json
{ "name": "Review" }
```

Response `201 Created`:

```json
{ "id": 13, "name": "Review", "position": 3 }
```

#### Đổi thứ tự cột

| Method | Endpoint |
|---|---|
| `PATCH` | `/api/v1/boards/{boardId}/columns/order` |

Request:

```json
{ "orderedColumnIds": [10, 13, 11, 12] }
```

Response `200 OK`: danh sách cột sau khi xếp.

### Card (Task)

Thuộc `task-service`. Giữ thẻ, người được gán, bình luận.
`boardId` và `columnId` chỉ là số tham chiếu sang board-service.
`assigneeId` chỉ là UUID tham chiếu sang user-service.

#### Tạo thẻ

| Method | Endpoint |
|---|---|
| `POST` | `/api/v1/boards/{boardId}/cards` |

Request:

```json
{
  "columnId": 10,
  "title": "Dựng skeleton Dev-Platform",
  "description": "pom + gateway + api.md",
  "assigneeId": "c0a8b1e2-0000-4000-8000-000000000001",
  "dueDate": "2026-10-15T17:00:00Z"
}
```

Response `201 Created`:

```json
{
  "id": 100,
  "boardId": 1,
  "columnId": 10,
  "title": "Dựng skeleton Dev-Platform",
  "status": "TODO",
  "position": 0,
  "assigneeId": "c0a8b1e2-0000-4000-8000-000000000001"
}
```

`position` là thứ tự thẻ trong cột, bắt đầu từ 0.

#### Lấy thẻ theo bảng

| Method | Endpoint |
|---|---|
| `GET` | `/api/v1/boards/{boardId}/cards?columnId=10` |

`columnId` là query tùy chọn để lọc. Response `200 OK`: mảng thẻ
sắp theo `position`.

#### Kéo thẻ qua cột (move)

| Method | Endpoint |
|---|---|
| `PATCH` | `/api/v1/cards/{cardId}/move` |

Request:

```json
{
  "toColumnId": 11,
  "toPosition": 0
}
```

Response `200 OK`: thẻ sau khi chuyển, kèm `columnId` và `position` mới.

Server phải dồn `position` của các thẻ còn lại trong cột cũ và cột mới
trong cùng một transaction. Client lạc quan (optimistic) vẽ trước, server
là chuẩn cuối cùng.

#### Gán người làm

| Method | Endpoint |
|---|---|
| `PATCH` | `/api/v1/cards/{cardId}/assignee` |

Request:

```json
{
  "assigneeId": "c0a8b1e2-0000-4000-8000-000000000001"
}
```

Khi gán mới, task-service bắn event cho notification-service để báo
người được gán. Không báo chính người thao tác.

#### Bình luận

| Method | Endpoint |
|---|---|
| `POST` | `/api/v1/cards/{cardId}/comments` |

Request:

```json
{
  "content": "@an review giúp em đoạn move",
  "mentionedUserIds": ["c0a8b1e2-0000-4000-8000-000000000002"]
}
```

Response `201 Created`: bình luận vừa tạo. Người trong
`mentionedUserIds` nhận thông báo mention.

### Notification

Thuộc `notification-service`. Chỉ đọc và đánh dấu đã đọc.
Không cho client tự tạo thông báo.

#### Lấy thông báo của tôi

| Method | Endpoint |
|---|---|
| `GET` | `/api/v1/notifications/me?unreadOnly=true` |

Response `200 OK`:

```json
[
  {
    "id": 500,
    "type": "ASSIGNED",
    "title": "Bạn được gán thẻ MSS-12",
    "cardId": 100,
    "boardId": 1,
    "read": false,
    "createdAt": "2026-09-29T10:30:00Z"
  }
]
```

Các `type` hợp lệ: `ASSIGNED`, `MENTIONED`, `MOVED`.

#### Đánh dấu đã đọc

| Method | Endpoint |
|---|---|
| `PATCH` | `/api/v1/notifications/{id}/read` |

Response `200 OK`: thông báo sau khi đổi `read` thành `true`.

### Real-time

Web React giữ một kênh sống (WebSocket) theo bảng:

```text
WS /ws/boards/{boardId}
```

Header: `Authorization: Bearer <jwt>`.

Event server bắn xuống:

```json
{ "event": "card.moved", "cardId": 100, "toColumnId": 11, "toPosition": 0 }
```

```json
{ "event": "card.created", "cardId": 101, "columnId": 10 }
```

```json
{ "event": "comment.added", "cardId": 100, "commentId": 900 }
```

Mobile Flutter không giữ socket suốt. Nhận việc gán và mention qua đẩy
(push FCM). Bấm vào tin mới mở app và gọi API lấy chi tiết.

Khi mất mạng rồi nối lại, client phải gọi lại `GET` bảng và thẻ.
Server là chuẩn cuối cùng, client chỉ vẽ lại.

### Quy ước lỗi cũ

Format thống nhất:

```json
{
  "code": "CARD_NOT_FOUND",
  "message": "Card does not belong to this board",
  "timestamp": "2026-09-29T10:30:00Z",
  "path": "/api/v1/cards/100/move"
}
```

| Status | Trường hợp |
|---|---|
| `200 OK` | Lấy hoặc cập nhật thành công |
| `201 Created` | Tạo bảng, thẻ, bình luận thành công |
| `204 No Content` | Xóa thành công |
| `400 Bad Request` | Thiếu field, `position` âm, cột không thuộc bảng |
| `401 Unauthorized` | Chưa xác thực |
| `403 Forbidden` | Không phải member của bảng |
| `404 Not Found` | Không thấy bảng, thẻ, thông báo |
| `409 Conflict` | Trùng key bảng, trùng email đăng ký |

### Đề xuất Kỳ 2 cũ — không còn là quyết định hiện hành

Các gạch đầu dòng sau được giữ để tra cứu lịch sử; không dùng làm kiến trúc Kỳ 2 hiện hành. Phạm vi service/API Kỳ 2 được hoãn tới khi có thiết kế riêng trong [architecture decisions](../project/architecture-decisions.md).

- `Board` thành nền cho `Issues` (mỗi issue là một card đặc biệt).
- `Task` thành nền cho `Branch/Commit/PR` (mỗi PR gắn một `cardId`).
- `Notification` giữ nguyên, thêm `type` mới như `PR_REVIEWED`.
- Gateway thêm route `/api/v1/repos/**`, `/api/v1/workflows/**`.
- Giữ tiền tố `/api/v1` để mobile cũ còn chạy khi API lên `v2`.
