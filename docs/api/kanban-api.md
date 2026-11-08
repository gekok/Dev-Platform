# Kanban API Kỳ 1 — trạng thái chuyển sang Project-first

**Trạng thái:** Hướng resource Project-first và các nghiệp vụ chính đã thống nhất. Đây chưa phải OpenAPI/schema cuối cùng: payload Invitation, một số mã lỗi, status operation, query cursor và event contract còn mở trong [architecture decisions](../project/architecture-decisions.md).

**Base URL mục tiêu qua Gateway:** `http://localhost:8080/api/v1`.

**Runtime hiện tại:** backend vẫn chủ yếu dùng Board-first API. `user-service` đã triển khai login và `/users/me`; endpoint `/users/me` xác thực Bearer token bằng RSA256. Gateway xác thực JWT, kiểm tra Account snapshot Redis và ghi đè `X-User-Id` cho request đi qua Gateway; một số controller legacy vẫn dùng header này. Direct-service ingress chưa bị chặn, còn Gateway→Redis snapshot sync chưa được xác nhận end-to-end. Phần Board-first ở cuối tài liệu được giữ làm tham chiếu cho code/tài liệu cũ, không phải contract để phát triển tính năng Project-first mới.

## 1. Route map mục tiêu Project-first

Các route dưới đây ghi lại phạm vi đã thống nhất và route đang được dùng làm tham chiếu trong React/MSW demo. Payload, response, lỗi và mức độ đã implement không được suy ra chỉ từ route map này.

| Nhóm | Method | Endpoint | Hành vi mục tiêu |
|---|---|---|---|
| Auth | `POST` | `/api/v1/auth/login` | Đăng nhập theo contract hiện có |
| Auth | `GET` | `/api/v1/auth/public-key` | Public key RS256 cho Gateway; public |
| User | `GET` | `/api/v1/users/me` | Lấy tài khoản hiện tại |
| User directory | `GET` | `/api/v1/users?query={text}&limit=20` | Runtime handler tìm tài khoản; privacy/query contract cần chốt |
| User profile | `GET` | `/api/v1/users/profiles?ids={uuid,...}` | Runtime handler lấy profile theo UUID; route rộng hơn thiết kế tombstone mục tiêu |
| Project | `GET`, `POST` | `/api/v1/projects` | Liệt kê và tạo Project |
| Project | `GET` | `/api/v1/projects/{projectId}` | Xem Project |
| Membership | `GET` | `/api/v1/projects/{projectId}/members` | Liệt kê Member chính thức |
| Invitation | `POST` | `/api/v1/projects/{projectId}/invitations` | Owner mời tài khoản đã tồn tại; trạng thái ban đầu `PENDING` |
| Invitation | `GET` | `/api/v1/projects/{projectId}/invitations?status=PENDING` | Owner xem các lời mời của Project để theo dõi/hủy; route đề xuất |
| Invitation | `GET` | `/api/v1/projects/invitations` | Danh sách lời mời gửi tới tài khoản hiện tại; recipient lấy từ identity đã xác thực |
| Invitation | `POST` | `/api/v1/projects/{projectId}/invitations/{inviteId}/accept` | Người được mời chấp nhận; lúc này mới tạo Membership |
| Invitation | `POST` | `/api/v1/projects/{projectId}/invitations/{inviteId}/reject` | Người được mời từ chối; route đề xuất |
| Invitation | `DELETE` | `/api/v1/projects/{projectId}/invitations/{inviteId}` | Owner hủy lời mời; route đề xuất |
| Membership | `DELETE` | `/api/v1/projects/{projectId}/members/{userId}` | Owner gỡ Member hoặc Member tự rời Project; Owner duy nhất không thể tự gỡ; trả operation handle khi Redis apply còn pending |
| Board | `GET`, `POST` | `/api/v1/projects/{projectId}/boards` | Liệt kê và tạo Board trong Project |
| Board | `GET` | `/api/v1/boards/{boardId}` | Xem Board và Columns |
| Column | `POST` | `/api/v1/boards/{boardId}/columns` | Tạo Column |
| Column | `PATCH` | `/api/v1/boards/{boardId}/columns/order` | Sắp xếp lại Columns |
| Card | `GET`, `POST` | `/api/v1/projects/{projectId}/boards/{boardId}/cards` | Liệt kê và tạo Card trong Project/Board |
| Card | `GET` | `/api/v1/projects/{projectId}/cards/{cardId}` | Xem Card trong Project |
| Card | `PATCH` | `/api/v1/projects/{projectId}/cards/{cardId}/move` | Chuyển Card |
| Card | `PATCH` | `/api/v1/projects/{projectId}/cards/{cardId}/assignee` | Gán người xử lý |
| Comment | `GET`, `POST` | `/api/v1/projects/{projectId}/cards/{cardId}/comments` | Xem và tạo Comment |
| Notification | `GET` | `/api/v1/notifications/me` | Liệt kê Notification của tài khoản hiện tại |
| Notification | `PATCH` | `/api/v1/notifications/{notificationId}/read` | Đánh dấu đã đọc |
| Realtime | `POST` | `/api/v1/realtime/boards/{boardId}/ws-ticket` | Đổi Bearer access token đã xác thực thành ticket WebSocket dùng một lần |
| Account Admin | `POST` | `/api/v1/admin/accounts` | System Admin tạo Account theo contract issue #54; role mặc định `USER` |
| Board preference | `GET` | `/api/v1/users/me/preferences/default-boards` | Route tham khảo từ demo; ownership/storage/access validation backend chưa chốt |
| Board preference | `PUT`, `DELETE` | `/api/v1/users/me/preferences/default-boards/{projectId}` | Route tham khảo từ demo; service sở hữu và behavior khi Project/Board bị xóa chưa chốt |

### Quy tắc đã thống nhất và phần còn mở

- Project là ranh giới membership; Board thuộc một Project. ID Project, Board, Column, Invitation, Card, Comment và Notification là số; User dùng UUID.
- Owner và Member có quyền thao tác công việc như nhau. Owner có thêm quyền tạo Board, quản lý trạng thái Project/Board và membership.
- Kỳ 1 chỉ thêm tài khoản đã có trong hệ thống bằng Invitation; người nhận chấp nhận mới trở thành Member. Mời người ngoài hệ thống và đổi vai trò để Kỳ 2.
- Owner cần tra cứu tài khoản đang hoạt động để chọn người mời; directory/bulk profile handlers đã có source, nhưng policy quyền tra cứu, privacy và field contract vẫn cần chốt.
- Project và Board dùng `ACTIVE`, `COMPLETED`, `CANCELLED`; transition cụ thể và route cập nhật trạng thái chưa chốt.
- API list Card dùng cursor: mặc định 5, tối đa 20, `position DESC, id DESC`, cursor opaque gồm `position` và `id`. Cặp `(position,id)` đã tạo thứ tự toàn phần vì `id` là duy nhất. Wire proposal cho infinite-scroll riêng từng Column yêu cầu `columnId` và cursor ràng buộc với `boardId` + `columnId` + sort; đây là scope API/UI cần duyệt, không phải điều kiện toán học của cursor.
- Gateway hiện kiểm tra token RS256, issuer, `exp`, `iat` hiện diện và UUID `sub`, sau đó đọc Account snapshot Redis; chỉ Account `ACTIVE` được đi tiếp. Gateway gỡ `X-User-Id` do client gửi, đặt giá trị từ `sub` và giữ Authorization Bearer.
- User-service phát JWT gồm `iss`, `sub` UUID, `email`, `fullName`, `roles`, `authVersion`, `iat`, `exp`; issuer mặc định `dev-platform-user-service`, hạn dùng mặc định 1 giờ (`app.jwt.expiration-ms`). JWT được ký nhưng không mã hóa; email claim vẫn có trong Bearer token được Gateway forward. Token phát trước khi có issuer claim không hợp lệ với cấu hình mới; cần đăng nhập lại. Gateway không forward profile/role riêng trong header.
- Public routes Gateway: `POST /api/v1/auth/login`, `GET /api/v1/auth/public-key`, `GET /actuator/health`. Protected request trả `401` với token thiếu/sai, `403` nếu snapshot cho biết Account không `ACTIVE`, `503` nếu key/snapshot không xác minh được.
- Account snapshot key là `auth:account:{accountId}`, shape `{accountId,status,systemRoles,authVersion}`. Gateway đọc trực tiếp Redis mỗi request, không sở hữu hoặc cache snapshot. User-service ghi snapshot-sync job cùng Account mutation; worker mặc định tắt nên auth fail-closed nếu snapshot chưa được apply.
- Gateway chỉ bảo vệ request qua Gateway; direct-service ports chưa bị giới hạn network ingress. WebSocket Origin allowlist có cấu hình riêng bằng `GATEWAY_WS_ALLOWED_ORIGINS` (mặc định `http://localhost:5173`); CORS cho HTTP cross-origin chưa cấu hình.
- Task có secured internal lookup `GET /internal/boards/{boardId}` tới board-service bằng `X-Service-Token` để lấy trạng thái Board/Project/Columns. Endpoint này không thay thế Project membership authorization: Task vẫn đọc authorization snapshot từ Redis. Internal lookup không được expose qua Gateway; direct-service ingress vẫn chưa bị chặn.
- Tạo Project yêu cầu Account nghiệp vụ `ACTIVE` đã được Gateway xác thực; board-service lấy actor từ identity context và ghi Project cùng Owner membership trong một transaction. Account mang `SYSTEM_ADMIN` không được tạo hoặc tham gia Project; cá nhân cần làm việc trong Project dùng Account nghiệp vụ không mang role này.
- SQL bootstrap local seed Account System Admin mẫu trong `user_db`; Kỳ 1 không dùng Flyway. Issue #7 đã chốt chỉ System Admin được tạo Account, không mở public self-registration và Account mang `SYSTEM_ADMIN` không được làm Owner/Member trong Project. Admin create/status/role routes có implementation; field `roles` trong request tạo Account trái với quyết định không để client tự khai role, nên là sai khác implementation chưa được duyệt. Contract mục tiêu và credential provisioning cần được đồng bộ riêng. Shared consumer factory có retry tối đa 3 lần, cách nhau 5 giây, và DLX; Notification/Task cleanup có queue/DLQ source nhưng broker E2E, xử lý sau DLQ và Outbox Relay ACK-loss còn mở. Directory/profile handlers có source; privacy, query rules và field contract, cùng lỗi nghiệp vụ Invitation, expiry/cancel, transition Project/Board, Card status, cursor wire/scope và Board preference còn mở hoặc ở mức đề xuất. Account deletion/cascade thuộc Kỳ 2, không nằm trong API Kỳ 1.

Các route preference và nhiều route Kanban bắt nguồn từ MSW demo; Invitation/Accept và quyền Project-first là quyết định backend sau đó, không phải endpoint đã có trong demo. Xem [chỉ mục tài liệu](../README.md) và [architecture decisions](../project/architecture-decisions.md) để biết thứ tự ưu tiên.

## 2. Contract chi tiết Project-first mục tiêu (bản thiết kế)

Phần này bổ sung payload/response và quyền theo service để làm nền cho implementation. Đây là **bản thiết kế**, không phải OpenAPI đã duyệt hoặc bằng chứng backend đã chạy. Mỗi điểm được ghi `Đã chốt`, `Đề xuất` hoặc `Còn mở`; khi sửa một mục đang mở, cập nhật architecture decisions và các tài liệu service cùng lúc.

### Quy ước chung

- Client gọi qua Gateway và gửi `Authorization: Bearer <accessToken>`. Gateway xác thực token rồi ghi đè `X-User-Id` theo `sub`; user-service vẫn nhận Bearer token để `/users/me` tự xác minh. Các service khác không được lấy actor ID hoặc role từ header/body do client kiểm soát.
- Account không ở trạng thái `ACTIVE` không được dùng để cấp hoặc giữ quyền. Kỳ 1 chưa triển khai Account deletion; trạng thái Account và authorization snapshot phải được xác minh qua nguồn có thẩm quyền, không xác minh được thì fail closed.
- Project, Board, Column, Invitation, Card, Comment và Notification dùng ID số; Account dùng UUID. ID giữa database/service chỉ là tham chiếu logic, không tạo FK chéo database.
- Lỗi mục tiêu dùng envelope `{ "code", "message", "timestamp", "path" }` theo format đang được tham chiếu trong tài liệu. Mã lỗi cụ thể phải ổn định theo từng nghiệp vụ; mapping nào chưa chốt được đánh dấu ở dưới.
- Thành công ghi dữ liệu đồng bộ chỉ được trả sau khi business data, Outbox và durable snapshot-sync job cùng commit. Khi gỡ Member, Redis apply snapshot mới là mốc thu hồi quyền; nếu apply còn pending thì trả `202` + `operationId`/`statusUrl`. Task unassign chạy nền độc lập, không chặn revoke completion. Task đọc snapshot từ Redis; snapshot thiếu/lỗi không được xác minh thì fail closed.
- Mapping HTTP khung đề xuất: `401` chưa xác thực; `403` không có quyền; `404` resource không tồn tại/không thuộc phạm vi đọc; `400` input sai; `409` xung đột trạng thái/nghiệp vụ; `503 AUTHORIZATION_UNAVAILABLE` khi không thể chứng minh revoke/fence đã có hiệu lực và phải fail closed. Status/error code cụ thể vẫn cần chốt.

### `user-service`: Auth và System Admin

Role hệ thống được lưu trong `SystemRoles` và `UserSystemRoles` thuộc `user_db`; Account mang `SYSTEM_ADMIN` chỉ được quản trị Account, không được làm Owner/Member trong Project (theo quyết định #7). SQL bootstrap local seed Account System Admin mẫu; Kỳ 1 không dùng Flyway. Kỳ 1 không triển khai Account deletion.

| Method | Route mục tiêu | Request | Thành công | Quyền/trạng thái |
|---|---|---|---|---|
| `POST` | `/api/v1/auth/login` | `{ "email": "...", "password": "..." }` | `200 { "accessToken": "...", "tokenType": "Bearer" }` | Public; chỉ Account `ACTIVE`; JWT RS256 với `iss`, `sub`, `email`, `fullName`, `roles`, `authVersion`, `iat`, `exp`; mặc định 1 giờ |
| `GET` | `/api/v1/auth/public-key` | Không body | `200 { "algorithm": "RSA256", "publicKey": "PEM" }` | Public; Gateway lấy key này để kiểm tra JWT |
| `GET` | `/api/v1/users/me` | `Authorization: Bearer <accessToken>` | `200 { "id": "uuid", "email": "...", "fullName": "..." }` | Account hiện tại; service tự xác minh Bearer token |
| `GET` | `/api/v1/users?query={text}&limit=20` | Query tìm theo tên/email | Runtime `200 UserCandidate[]`: `id`, `fullName`, `email` | User đã xác thực; privacy, query/limit rules còn mở |
| `GET` | `/api/v1/users/profiles?ids={uuid,...}` | UUID batch trong query | Runtime `200 UserProfile[]`: `id`, `displayName` | User đã xác thực; tối đa 100 IDs; tombstone chưa có trong runtime |
| `POST` | `/api/v1/admin/accounts` | `{ "email": "...", "password": "...", "fullName": "..." }` | `201 UserResponse` (`id`, `email`, `fullName`, `status`, `roles`, `authVersion`) | Chỉ System Admin; role luôn là `USER`; request không có field `roles` (#54) |

Runtime `UserCandidate` trả `id`, `fullName`, `email`; runtime `UserProfile` trả `id`, `displayName`. Target design còn đề xuất tombstone `accountDeleted`, chưa có trong runtime. Email hiện được trả bởi directory handler; policy ai được tra cứu email và cách client compose directory với Membership do board-service sở hữu vẫn cần chốt.

Tạo/quản trị Account qua System Admin thuộc nghiệp vụ quản trị hệ thống, không cấp quyền Project. API xóa Account và operation status chưa thuộc Kỳ 1; soft delete, Owner succession và xử lý cascade được bàn ở Kỳ 2.

### `board-service`: Project, Membership, Invitation, Board, Column

| Method | Route mục tiêu | Request/Query | Thành công | Quyền chính |
|---|---|---|---|---|
| `GET` | `/api/v1/projects` | Không body | `200 ProjectSummary[]` | Member/Owner thấy Project mình tham gia |
| `POST` | `/api/v1/projects` | `{ "name": "MSS Platform" }`; không nhận `ownerId`/`userId` của actor | `201 ProjectSummary`; người tạo thành Owner | Account nghiệp vụ `ACTIVE`, danh tính do Gateway xác thực; Account mang `SYSTEM_ADMIN` bị từ chối; dùng Account nghiệp vụ không mang role này để làm Project |
| `GET` | `/api/v1/projects/{projectId}` | Path ID | `200 ProjectDetail` | Member/Owner |
| `GET` | `/api/v1/projects/{projectId}/members` | Path ID | `200 MemberSummary[]` | Member/Owner |
| `POST` | `/api/v1/projects/{projectId}/invitations` | `{ "userId": "uuid" }` | `201 Invitation` có `status: PENDING` | Owner; tài khoản đã tồn tại |
| `GET` | `/api/v1/projects/{projectId}/invitations?status=PENDING` | `status` tùy chọn | `200 Invitation[]` (đề xuất) | Owner xem lời mời của Project để theo dõi/hủy |
| `GET` | `/api/v1/projects/invitations` | Không body | `200 Invitation[]` | Người nhận hiện tại; recipient suy ra từ identity |
| `POST` | `/api/v1/projects/{projectId}/invitations/{inviteId}/accept` | Không body | `201 Membership` (đề xuất; không gắn body với `204`) | Chỉ người nhận; accept + tạo Membership cùng transaction |
| `POST` | `/api/v1/projects/{projectId}/invitations/{inviteId}/reject` | Không body | `204 No Content` (đề xuất) | Chỉ người nhận; route đề xuất |
| `DELETE` | `/api/v1/projects/{projectId}/invitations/{inviteId}` | Không body | `204` (đề xuất) | Owner hủy Invitation; route đề xuất |
| `DELETE` | `/api/v1/projects/{projectId}/members/{userId}` | Path IDs | `202 { "operationId": "uuid", "status": "PENDING", "statusUrl": "..." }` khi Redis snapshot còn chờ apply | Owner gỡ Member; Member chỉ tự rời; Owner không tự gỡ |
| `GET`, `POST` | `/api/v1/projects/{projectId}/boards` | Create: `{ "name": "...", "key": "ops", "columns": ["To Do"] }` | `200 BoardSummary[]` / `201 BoardDetail` | List Member/Owner; tạo Board chỉ Owner |
| `GET` | `/api/v1/boards/{boardId}` | Path ID | `200 BoardDetail` kèm Columns | Member/Owner |
| `POST` | `/api/v1/boards/{boardId}/columns` | `{ "name": "Review" }` | `201 Column` | Owner và Member theo quyền đã chốt |
| `PATCH` | `/api/v1/boards/{boardId}/columns/order` | `{ "orderedColumnIds": [1, 2, 3] }` | `200 Column[]` | Quyền reorder còn mở |

Project creation: `board-service` chỉ dùng actor ID từ identity context tin cậy do Gateway chuyển tiếp, không dùng `userId` trong payload hoặc header client tự gửi. Project và Membership `OWNER` được ghi cùng transaction. Request không có danh tính hợp lệ bị từ chối theo quy tắc `401`; danh tính đã xác thực nhưng không có quyền Account nghiệp vụ bị từ chối theo quy tắc `403`. Khi kiểm tra trạng thái Account cần nguồn xác minh vì internal REST chưa bảo mật; không xác minh được thì fail closed. Account deletion thuộc Kỳ 2.

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

Gỡ Member: board-service ghi Membership change, Outbox và snapshot-sync job trong cùng transaction. `authorizationVersion` tăng và là Membership `aggregateVersion`, Redis CAS/job retry dùng version này; `membershipVersion` giữ generation đã bị thu hồi để cleanup assignment. Redis apply snapshot mới là mốc thu hồi quyền; khi còn pending, API trả `202` cùng `operationId`/`statusUrl`. Snapshot job lưu `operationId`; job status `SYNCED` ánh xạ thành operation status `COMPLETED` khi đúng version đã apply hoặc đã tồn tại. Nếu version mới hơn vượt qua, job và operation mang status `SUPERSEDED`; đây không phải xác nhận revoke đã có hiệu lực và client cần tải lại Membership. Status route và quyền đọc trạng thái chưa triển khai trong skeleton. Task-service unassign Card ở job nền độc lập, giữ Comment/history và chỉ xóa assignment có đúng `membershipVersion`; cleanup thất bại không khôi phục quyền.

### Quy tắc lỗi mục tiêu (mã cụ thể đang là đề xuất)

| Tình huống | HTTP đề xuất | Error code đề xuất |
|---|---:|---|
| Project/Invitation/Member không tồn tại hoặc không thuộc phạm vi đọc | `404` | `PROJECT_NOT_FOUND`, `INVITATION_NOT_FOUND`, `MEMBER_NOT_FOUND` |
| Người gọi không phải Member/Owner, hoặc không phải Owner khi thao tác quản trị | `403` | `PROJECT_ACCESS_DENIED`, `PROJECT_OWNER_REQUIRED` |
| User ID mời không tồn tại | `404` | `USER_NOT_FOUND` |
| User không ở trạng thái `ACTIVE` | `409` | `USER_NOT_ACTIVE` |
| User đã là Member hoặc đã có Invitation PENDING | `409` | `MEMBER_ALREADY_EXISTS`, `INVITATION_ALREADY_PENDING` |
| Invitation không còn PENDING / Project không ACTIVE | `409` | `INVITATION_NOT_PENDING`, `PROJECT_NOT_ACTIVE` |
| Owner cố tự gỡ mình | `409` | `OWNER_CANNOT_LEAVE_PROJECT` |

Các mã trên là gợi ý để UI/API cùng xử lý; chưa phải error contract đã duyệt. Request ngoài Gateway không có identity tin cậy; không lấy `userId` trong payload làm actor.

Project/Board status dùng `ACTIVE`, `COMPLETED`, `CANCELLED`, nhưng chưa có route/status transition được chốt. Chưa thêm `PATCH /status` vào route hợp đồng. Khi transition được duyệt, bổ sung route và ma trận thao tác đọc/ghi.

### `task-service`: Card, assignment, Comment

| Method | Route mục tiêu | Request/Query | Thành công | Quyền chính |
|---|---|---|---|---|
| `GET` | `/api/v1/projects/{projectId}/boards/{boardId}/cards?columnId={columnId}&limit=5&cursor={opaque}` | Per-Column pagination proposal: `columnId` bắt buộc; `limit` mặc định 5, tối đa 20; `cursor` tùy chọn | `200 CardPage` | Member/Owner; cursor sort `position DESC, id DESC`, gắn Project + Board + Column |
| `POST` | `/api/v1/projects/{projectId}/boards/{boardId}/cards` | `{ "columnId": 10, "title": "...", "description": null, "assigneeId": null, "dueDate": null }` | `201 Card` | Member/Owner; assignee thuộc Project |
| `GET` | `/api/v1/projects/{projectId}/cards/{cardId}` | Path IDs | `200 CardDetail` | Member/Owner; Project ID phải khớp Card đã lưu |
| `PATCH` | `/api/v1/projects/{projectId}/cards/{cardId}/move` | `{ "toColumnId": 11, "toPosition": 0 }` | `200 Card` | Member/Owner; Column đích cùng Board/Project |
| `PATCH` | `/api/v1/projects/{projectId}/cards/{cardId}/assignee` | `{ "assigneeId": "uuid" }`; `null` để bỏ gán | `200 Card` | Member/Owner; assignee phải là Project Member |
| `GET` | `/api/v1/projects/{projectId}/cards/{cardId}/comments` | Path IDs | `200 Comment[]` | Member/Owner; Project ID phải khớp Card |
| `POST` | `/api/v1/projects/{projectId}/cards/{cardId}/comments` | `{ "content": "...", "mentionedUserIds": ["uuid"] }` | `201 Comment` | Member/Owner; mentioned users là Project Member |

Envelope phân trang đề xuất:

```json
{
  "items": [{ "id": 42, "projectId": 5, "boardId": 7, "columnId": 10, "title": "Prepare release", "position": 8, "assigneeId": null }],
  "nextCursor": "opaque-token-or-null",
  "hasMore": true
}
```

Thứ tự đã chốt là giảm dần theo `(position, id)`. `id` unique nên cặp này total order kể cả khi position lặp giữa Column. Vì UI tải vô hạn riêng từng Column, wire proposal cho endpoint trên yêu cầu `columnId`; cursor phải chỉ dùng lại với cùng `projectId`, `boardId`, `columnId` và sort contract, nếu sai phạm vi thì trả `400 INVALID_CURSOR` (đề xuất). Token opaque có thể chứa hoặc ký scope cùng `{position,id}`. Tên query, shape `items/nextCursor/hasMore`, lỗi cursor và snapshot consistency vẫn cần duyệt. Backend đọc trạng thái hiện hành mỗi request, không hứa snapshot pagination. Client dedupe `cardId`, dùng realtime nếu có và hiện banner đầu bảng yêu cầu reload khi phát hiện bất nhất.

Card response tối thiểu đề xuất có `id`, `projectId`, `boardId`, `columnId`, `title`, `position`, `assigneeId`, cùng `description`/`dueDate` ở detail. Task lưu `project_id` scalar và mỗi read/write phải khớp `projectId` trên route với Card đã lưu. `status` hiện có trong demo (`TODO`, `IN_PROGRESS`, `DONE`) nhưng không đổi theo move; cần chốt có bỏ field này hay ràng buộc nó với Column. Comment giữ `id`, `cardId`, `authorId`, `content`, `createdAt`; `mentionedUserIds` là request input, response/storage model cần thống nhất.

Board/Column thuộc Project phải được xác minh từ nguồn đáng tin cậy trước khi bật create-card end-to-end. Task có secured Board-state lookup bằng `X-Service-Token` cho WebSocket, nhưng Column validator cho create/move chưa có adapter sẵn sàng; route `projectId` do client gửi không tự chứng minh quan hệ đó.

### `notification-service`: Notification

| Method | Route mục tiêu | Request/Query | Thành công | Quyền |
|---|---|---|---|---|
| `GET` | `/api/v1/notifications/me?unreadOnly=false` | `unreadOnly` mặc định false | `200 Notification[]` | Chỉ Notification của Account hiện tại |
| `PATCH` | `/api/v1/notifications/{notificationId}/read` | Không body | `200 Notification` đã đọc | Chỉ recipient |

Shape tham khảo: `{ id, type, title, projectId, cardId, boardId, read, createdAt }`; `projectId`, `cardId`, `boardId` có thể null với Notification hệ thống hoặc Notification không gắn entity tương ứng. Kỳ 1 Task producer phát `card.assigned` → `ASSIGNED` cho assignee mới khác actor, và `comment.mentioned` → `MENTIONED` cho recipient IDs duy nhất còn là Project Member khi Comment được ghi, trừ tác giả. Không phát `MOVED`. Notification consumer tạo một record cho mỗi recipient trong transaction Inbox; title được tạo ở Notification service từ event type/context, không lấy từ Comment body. Task event payload chỉ mang recipient IDs và context IDs, không chứa Card title/Comment body. Producer Outbox và Notification consumer đều đã có trong source; consumer đã được kiểm thử bằng publish trực tiếp qua RabbitMQ local, còn Task Outbox Relay và ACK-loss/redelivery vẫn thuộc integration task. Notification được tạo từ event, không cho client tự tạo. Internal route hiện là skeleton và bị tắt mặc định; nếu bật, nó vẫn chưa có bảo vệ service-to-service và không được expose qua Gateway.

Account/Project deletion và Notification retention thuộc Kỳ 2. Notification Kỳ 1 thuộc Account nhận; Project/Board/Card chỉ là context, không có cascade delete theo Project.

### Board preference (chỉ từ demo, chưa phải service contract)

MSW demo có `GET /api/v1/users/me/preferences/default-boards`, `PUT /api/v1/users/me/preferences/default-boards/{projectId}` với `{ "boardId": 1 }`, và `DELETE` cùng path (có query `expectedBoardId`). Service sở hữu, DB, kiểm tra Member/Board còn hoạt động và cleanup khi Project/Board bị xóa đều chưa chốt; không coi các route này là API backend đã duyệt.

### Dữ liệu thay đổi khi đang cuộn

Backend đọc trạng thái hiện hành cho từng request. `position` đổi khi Card di chuyển; do đó dùng keyset cursor, không dùng offset/page-number. Inbox/event dedupe không giải quyết pagination. Khi realtime hoặc phản hồi phát hiện bất nhất, UI phải hiện banner đầu bảng yêu cầu reload thay vì âm thầm gộp danh sách sai. Cách phát hiện stale cursor và HTTP error cụ thể còn mở.

### Độ sẵn sàng

Các route trên là target design; Board-first controllers hiện tại không cung cấp đầy đủ route Project-first. Gateway JWT filter đã có implementation và test source, nhưng Account snapshot production chưa được apply end-to-end; thiếu snapshot sẽ trả `503`. Project/Membership entities, cursor API, invitation API, permission checks và cleanup workers cũng chưa hoàn chỉnh. Xem tài liệu API/schema riêng của service để đối chiếu source.

## 3. Hợp đồng Board-first cũ — chỉ tham khảo runtime

Phần dưới mô tả API Board-first được viết trước quyết định Project-first. Một số controller đang dùng các route này, nhưng nội dung cũ không đồng nghĩa mọi endpoint đã hoạt động hoàn chỉnh. Không dùng phần này làm thiết kế đích cho API mới.

Base URL cũ qua Gateway:

```text
http://localhost:8080/api/v1
```

Contract cũ mô tả JWT `sub` làm danh tính và database riêng cho từng service. Một số controller legacy còn đọc `X-User-Id`; request đi qua Gateway nhận giá trị đã xác minh, nhưng request gọi thẳng service có thể giả mạo header nếu network ingress vẫn mở. Xem ghi chú runtime ở [README](../../README.md).

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
    "projectId": 5,
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

Issue #24 architecture approved on 2026-11-06. Source đã có Gateway browser ticket, WebSocket proxy và Task Board lookup adapter; Task runtime vẫn phải chờ readiness gates và nghiệm thu integration.

Web React giữ một kênh sống (WebSocket) theo bảng:

```text
WS /ws/boards/{boardId}
```

Browser gọi `POST /api/v1/realtime/boards/{boardId}/ws-ticket` với `Authorization: Bearer <accessToken>`; endpoint chỉ nhận Account identity đã được Gateway xác thực và trả `201` cùng `{ "ticket": "...", "expiresInSeconds": 30 }`, header `Cache-Control: no-store`. Sau đó browser mở `WS /ws/boards/{boardId}?ticket=<ticket>` (hoặc `WSS` khi chạy HTTPS). Không gửi access token trong handshake vì WebSocket API của browser không cho đặt Authorization header tùy ý.

Ticket ngẫu nhiên, gắn với Account và Board, hết hạn sau 30 giây mặc định và chỉ dùng một lần bằng thao tác Redis nguyên tử. Gateway yêu cầu một `Origin` khớp chính xác allowlist; khi proxy, nó xóa ticket khỏi URL, bỏ `Authorization` cùng các header identity/security do client gửi và đặt `X-User-Id` từ ticket đã xác minh. Ticket thiếu/sai/hết hạn/đã dùng hoặc sai Board trả `401`; Origin không hợp lệ trả `403`; Redis lỗi trả `503`. Vì ticket nằm trong query string, ingress/reverse-proxy access log cần che giá trị `ticket`. Task không tin identity hoặc `projectId` do client gửi. Task resolve Board→Project qua secured internal lookup đã lưu, rồi kiểm tra Account và Project membership qua Redis snapshot lúc handshake, định kỳ khi socket mở và ngay trước mỗi lần gửi. Nếu không xác minh được identity, Board relation hoặc snapshot, Task từ chối handshake/đóng socket.

Fanout chỉ tới các session cùng Board trong Task instance hiện tại. Event được gửi sau khi transaction Card/Comment commit; send/queue lỗi có thể làm rơi event nhưng không đổi kết quả HTTP đã commit. WebSocket không lưu bền, không retry/replay và không bảo đảm thứ tự hay nhận đủ. Khi reconnect, client refetch REST. Event không chứa title hoặc Comment body. Đây là semantics cho WebSocket #24, không phải RabbitMQ catalog của #25.

Event server gửi xuống:

```json
{ "event": "card.moved", "cardId": 100, "toColumnId": 11, "toPosition": 0 }
```

```json
{ "event": "card.created", "cardId": 101, "columnId": 10 }
```

```json
{ "event": "comment.added", "cardId": 100, "commentId": 900 }
```

Task handler chỉ được đăng ký khi `TASK_TRUSTED_GATEWAY_IDENTITY_ENABLED`, `TASK_BOARD_PROJECT_LOOKUP_READY`, `TASK_GATEWAY_WEBSOCKET_PROXY_READY` và `TASK_BOARD_WEBSOCKET_ENABLED` đều bật; mặc định cả bốn đều tắt. Source có adapter gọi `GET /internal/boards/{boardId}` với `X-Service-Token`, và Gateway proxy đã được kiểm tra bằng upstream giả. Chưa có nghiệm thu WebSocket end-to-end với Task/Board/Redis thật; direct-service ingress restriction vẫn là điều kiện triển khai riêng.

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
