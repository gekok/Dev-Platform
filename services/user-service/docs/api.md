# user-service API và ranh giới triển khai

**Vai trò mục tiêu:** sở hữu Account, credential, Account status, `SystemRoles`/`UserSystemRoles` và Account authorization snapshot. Account deletion/cascade được hoãn sang Kỳ 2. Quyết định chung: [architecture decisions](../../../docs/project/architecture-decisions.md); route Project-first mục tiêu: [Kanban API](../../../docs/api/kanban-api.md).

Shared messaging của user-service: [hướng dẫn đọc và dùng](database.md#shared-modules-trong-user-service).

## Controller hiện có

`POST /api/v1/auth/register` khai báo đăng ký công khai (đã bãi bỏ), `POST /api/v1/auth/login` đăng nhập, `GET /api/v1/auth/public-key` xuất public key PEM, `GET /api/v1/users/me` lấy hồ sơ tài khoản hiện tại, `GET /api/v1/users` tìm kiếm ứng viên danh bạ, `GET /api/v1/users/profiles` lấy profile hiển thị sau khi xác minh quyền truy cập Project, `POST /api/v1/admin/accounts` tạo tài khoản bởi System Admin, `PATCH /api/v1/admin/users/{userId}/status` cập nhật trạng thái tài khoản, cùng các route `GET`, `POST`, `PUT`, `DELETE /api/v1/admin/users/{userId}/roles...` để xem và quản lý system role.

Endpoint nội bộ `GET /internal/users/{userId}` cho service khác xác minh Account; yêu cầu `X-Service-Token` và chỉ trả `status`. Route này không trả email hoặc tên hiển thị.

- Mapping `/auth/register` vẫn tồn tại trong controller nhưng là route cũ, đã bị bãi bỏ theo quyết định #7; ném `OPERATION_NOT_SUPPORTED`. Chính sách đã chốt là chỉ System Admin mới tạo Account.
- `/auth/login` gọi `LoginUserPort`; đã triển khai qua `LoginUserService` và `JwtTokenAdapter` sử dụng RSA256 (ký bằng Private Key, xác thực qua Public Key).
- `/users/me` xác thực token từ header `Authorization: Bearer <jwt>`, trích xuất subject UUID qua RSA256 và gọi `GetCurrentUserPort` (triển khai qua `GetCurrentUserService`); không cho phép client dùng `X-User-Id` giả mạo.
- `/admin/accounts`, `/admin/users/{userId}/status` và các route `/admin/users/{userId}/roles...` được xử lý bởi `AdminUserController`, yêu cầu Bearer token của tài khoản mang role `SYSTEM_ADMIN`. Gateway route `/api/v1/admin/**` tới user-service và áp dụng Account snapshot gate trước khi proxy. Thao tác cập nhật trạng thái/vai trò đảm bảo tính nguyên tử: tăng `auth_version`, ghi Outbox và snapshot-sync job trong cùng transaction.
- `/users` yêu cầu Account `ACTIVE`, giới hạn `limit` từ 1 đến 100 (mặc định 20), chỉ trả Account `ACTIVE` không mang `SYSTEM_ADMIN`, và không trả email.
- `/users/profiles` yêu cầu caller `ACTIVE` và Member/Owner `ACTIVE` của Project đang `ACTIVE`. User-service gửi UUID caller cùng `X-Service-Token` tới Board `GET /internal/projects/{projectId}/members/{userId}`; nếu Board không xác minh được membership thì request fail-closed bằng `503 AUTHORIZATION_UNAVAILABLE`.


## Thiết kế mục tiêu đã thống nhất

- System Admin quản lý vòng đời Account; Admin không có quyền nghiệp vụ trong Project.
- `SYSTEM_ADMIN` là quyền hệ thống, không phải Project role. Account mang role này không được tạo Project hoặc làm Owner/Member. Nếu cùng một cá nhân cần tham gia Project, dùng Account nghiệp vụ khác không mang `SYSTEM_ADMIN`.
- Owner duy nhất của Project không bị biến thành “admin hệ thống”. Account được mời chỉ thành Member sau khi chấp nhận Invitation.
- Owner cần chọn User đã có tài khoản để mời; user-service là nguồn chuẩn Account. Directory/profile handlers đã có trong `UserController`; field trả về đang là implementation hiện tại, còn privacy policy, query rules và consumer authorization cần được chốt.
- Schema mục tiêu lưu system role bằng `SystemRoles` và quan hệ `UserSystemRoles`. SQL bootstrap local seed Account System Admin mẫu và role tương ứng trong `user_db`; Kỳ 1 không dùng Flyway. Cách bootstrap cho môi trường triển khai có thể được thiết kế sau Kỳ 2.
- Khi Account status hoặc system role đổi, tăng `auth_version`, ghi Outbox event và durable snapshot-sync job cùng transaction trong user_db.
- user-service sở hữu Account authentication snapshot và retry apply vào Redis theo version. Snapshot version cũ không được ghi đè version mới.
- Account deletion, `DELETING`, deletion operation và tombstone không thuộc Kỳ 1; giữ soft delete/Owner succession cho Kỳ 2.

## Chưa triển khai hoặc cần chốt

- Login, provisioning Account qua Admin, quản lý system role và phát token đã có implementation source. Điều này chưa đồng nghĩa Gateway→Redis snapshot đã được tích hợp end-to-end.
- Account create/status/role mutation gọi snapshot recorder trong cùng DB transaction với thay đổi Account, Outbox và durable snapshot-sync job. SQL bootstrap local có Account System Admin mẫu; Kỳ 1 không dùng Flyway.
- Redis snapshot-sync worker kích hoạt qua `USER_AUTH_SNAPSHOT_SYNC_ENABLED` (mặc định `true` cho môi trường local để tự động sync snapshot của seed users và account mới sang Redis). Khi worker chưa chạy hoặc snapshot chưa tồn tại, Gateway fail-closed `503`; Gateway JWT filter và Directory/profile controller đã có source.
- Issue #7 đã chốt Account mang `SYSTEM_ADMIN` không được làm Owner/Member trong Project, chỉ System Admin tạo Account và không public self-registration. Nếu cùng một cá nhân cần làm việc trong Project, dùng Account nghiệp vụ khác không mang `SYSTEM_ADMIN`. Directory privacy và Account validation từ board-service vẫn còn mở.
- Xem [ghi chú Account deletion Kỳ 2](../../../docs/project/account-deletion-skeleton-guide.md); không dùng nó như hướng dẫn triển khai Kỳ 1.

## Nguồn code

`UserController`, `AdminUserController`, Account/role persistence, snapshot recorder và Redis worker nằm trong `services/user-service/src/main/java`. Snapshot sync cần bật worker để apply vào Redis; Account deletion classes thuộc phạm vi Kỳ 2 và không nằm trong runtime Kỳ 1.

## Chi tiết contract được khai báo trong source

Base path là `/api/v1`. Các route dưới đây là mapping của controller; trạng thái port bên dưới quyết định route có thể được khởi chạy đầy đủ hay chưa.

| Method | Path | Header/params | Request | Success |
|---|---|---|---|---|
| `POST` | `/api/v1/auth/register` | Không đọc identity header | `RegisterRequest` | `400`, `OPERATION_NOT_SUPPORTED` (Đã bãi bỏ) |
| `POST` | `/api/v1/auth/login` | Không đọc identity header | `LoginRequest` | `200`, `TokenResponse` |
| `GET` | `/api/v1/auth/public-key` | Không đọc identity header | Không có body | `200`, `{ "algorithm": "RSA256", "publicKey": "..." }` |
| `GET` | `/api/v1/users/me` | `Authorization: Bearer <jwt>` | Không có body | `200`, `UserResponse` |
| `GET` | `/api/v1/users` | Bearer của Account `ACTIVE`; `query`, `limit` 1–100 (mặc định 20) | Không có body | `200`, candidates `id` + `fullName`; excludes `SYSTEM_ADMIN` |
| `GET` | `/api/v1/users/profiles?projectId={id}&ids={uuid,...}` | Bearer của Account `ACTIVE`; `projectId` dương; tối đa 100 `ids` | Không có body | `200`, `400`, `403 PROJECT_MEMBERSHIP_REQUIRED`; `503 AUTHORIZATION_UNAVAILABLE` nếu Board không xác minh được membership |
| `POST` | `/api/v1/admin/accounts` | `Authorization: Bearer <admin_jwt>` | `AdminCreateAccountRequest` | `201`, `UserResponse` |
| `PATCH` | `/api/v1/admin/users/{userId}/status` | `Authorization: Bearer <admin_jwt>` | `ChangeStatusRequest` | `204 No Content` |
| `PUT` | `/api/v1/admin/users/{userId}/roles` | `Authorization: Bearer <admin_jwt>` | `UpdateUserRolesRequest` | `204 No Content` |
| `POST` | `/api/v1/admin/users/{userId}/roles` | `Authorization: Bearer <admin_jwt>` | `RoleRequest` | `204 No Content` |
| `DELETE` | `/api/v1/admin/users/{userId}/roles/{roleCode}` | `Authorization: Bearer <admin_jwt>` | Không có body | `204 No Content` |
| `GET` | `/api/v1/admin/users/{userId}/roles` | `Authorization: Bearer <admin_jwt>` | Không có body | `200`, `Set<String>` |

### Đăng ký

```json
{
  "email": "alex@example.com",
  "password": "secret123",
  "fullName": "Alex Morgan"
}
```

`email` phải đúng định dạng email và không rỗng; `password` dài 6–100 ký tự và tối đa 72 byte UTF-8 theo giới hạn của BCrypt; `fullName` không rỗng. Response không bao giờ gồm password/hash:

```json
{"id":"550e8400-e29b-41d4-a716-446655440000","email":"alex@example.com","fullName":"Alex Morgan"}
```

| Field | Kiểu JSON | Bắt buộc/validation | Ghi chú |
|---|---|---|---|
| `email` | string | Có; `@NotBlank`, `@Email` | Không có quy tắc chuẩn hóa chữ hoa/thường được khai báo ở DTO. |
| `password` | string | Có; `@NotBlank`, 6–100 ký tự, tối đa 72 byte UTF-8 | Chỉ nhận plaintext ở request; không trả password/hash trong response. |
| `fullName` | string | Có; `@NotBlank` | DTO không đặt giới hạn độ dài; cột JPA hiện là varchar(255). |

`UserResponse` có `id` (UUID), `email` (string), `fullName` (string). Status thành công là `201 Created`.

### Đăng nhập

Request gồm `email` hợp lệ và `password` không rỗng. Response:

```json
{"accessToken":"<token returned by login port>","tokenType":"Bearer"}
```

JWT hiện có claims `iss`, `sub`, `email`, `fullName`, `roles`, `authVersion`, `iat` và `exp`; issuer mặc định là `dev-platform-user-service`, thời hạn mặc định một giờ. Không có refresh-token flow. JWT được ký bằng RSA256, không mã hóa; Gateway chỉ dùng issuer, subject và time claims để xác thực, còn trạng thái Account lấy từ Redis snapshot. Trên môi trường production và multi-replica, bắt buộc cấu hình cùng một cặp khóa PEM ổn định cấp service qua `APP_JWT_PRIVATE_KEY`/`APP_JWT_PUBLIC_KEY`. Nếu cấu hình đường dẫn file qua `APP_JWT_KEY_FILE`, service hỗ trợ đọc secret mount chỉ đọc (read-only filesystem); khi chạy auto-generate mà không thể ghi file khóa ra disk, user-service sẽ dừng khởi động (fail-fast, không fallback in-memory không bền vững).

| Field | Kiểu JSON | Bắt buộc/validation |
|---|---|---|
| `email` | string | Có; `@NotBlank`, `@Email`, tối đa 255 ký tự |
| `password` | string | Có; `@NotBlank`, tối đa 72 byte UTF-8 |

Response `TokenResponse` có `accessToken` (string) và `tokenType` (string, hiện luôn là `Bearer`); status thành công là `200 OK`.

### Hồ sơ hiện tại

Controller xác thực danh tính trực tiếp từ header `Authorization: Bearer <token>`. Adapter `JwtTokenAdapter` xác thực tính hợp lệ của chữ ký RSA256, hạn sử dụng và trích xuất subject `UUID`. Client không thể truyền header hoặc parameter giả mạo để thay đổi tài khoản được đọc.

Response `UserResponse` có `id` (UUID), `email` (string), `fullName` (string), status thành công là `200 OK`. Không trả về mật khẩu, hash hay dữ liệu nhạy cảm.

*Ghi chú về ranh giới xác thực:* Gateway hiện xác thực Bearer token qua RSA256, kiểm tra issuer/thời hạn/subject, đọc Account snapshot Redis rồi ghi đè `X-User-Id` cho request proxy. `UserController.me()` và Admin controller vẫn tự kiểm tra Bearer token; Gateway giữ nguyên Authorization header khi forward. Endpoint `GET /api/v1/auth/public-key` cung cấp public key, được Gateway cache đến khi process restart. Direct-service ingress chưa bị chặn, nên service gọi thẳng vẫn chưa nằm sau boundary Gateway.

### Error mapping và khả năng khởi chạy

| Tình huống được handler bắt | HTTP | Mã code | Body |
|---|---:|---|---|
| Thiếu hoặc sai định dạng `Authorization` header, token sai chữ ký hoặc hết hạn (`AuthenticationException`) | `401` | `UNAUTHORIZED` | `{ "code": "UNAUTHORIZED", "message": "...", "timestamp": "...", "path": "..." }` |
| Tài khoản không ở trạng thái ACTIVE (`IllegalStateException`) | `403` | `FORBIDDEN` | `{ "code": "FORBIDDEN", "message": "Account is not active", "timestamp": "...", "path": "..." }` |
| Không tìm thấy tài khoản trong DB (`NoSuchElementException`) | `404` | `USER_NOT_FOUND` | `{ "code": "USER_NOT_FOUND", "message": "User not found: ...", "timestamp": "...", "path": "..." }` |
| Tham số không hợp lệ (`IllegalArgumentException`) | `400` | `BAD_REQUEST` | `{ "code": "BAD_REQUEST", "message": "...", "timestamp": "...", "path": "..." }` |
| Dữ liệu DTO không hợp lệ (`MethodArgumentNotValidException`) | `400` | `BAD_REQUEST` | `{ "code": "BAD_REQUEST", "message": "...", "timestamp": "...", "path": "..." }` |
| Thao tác không được hỗ trợ như tự đăng ký (`UnsupportedOperationException`) | `400` | `OPERATION_NOT_SUPPORTED` | `{ "code": "OPERATION_NOT_SUPPORTED", "message": "...", "timestamp": "...", "path": "..." }` |

`UserController` đã được trang bị đầy đủ bean: `LoginUserService` cho đăng nhập, `GetCurrentUserService` cho truy vấn hồ sơ, và `JwtTokenAdapter` cho ký/xác thực token RSA256. `RegisterUserPort` được đánh dấu deprecated theo quyết định #7.

## API mục tiêu thuộc user-service

User-service sở hữu Account/Auth và system role. Bảng dưới phân biệt API đã có controller runtime với API mục tiêu; contract chung chưa đồng nghĩa mọi route Project-first đã được nghiệm thu end-to-end:

`POST /api/v1/auth/register` còn mapping legacy nhưng luôn bị từ chối; Kỳ 1 không public self-registration. Theo issue #54, chỉ System Admin tạo Account qua `POST /api/v1/admin/accounts`; payload chỉ gồm `email`, `password`, `fullName`. Role không nhận từ payload và Account mới luôn nhận `USER`, trạng thái `ACTIVE`. System Admin nhập mật khẩu ban đầu; mật khẩu được băm bằng BCrypt strength 12.

| Method | Route mục tiêu | Request | Response | Quyền |
|---|---|---|---|---|
| `POST` | `/api/v1/auth/login` | `{ "email": "...", "password": "..." }` | `200 TokenResponse` | Public; chỉ Account `ACTIVE` |
| `GET` | `/api/v1/users/me` | Không body | `200 UserSummary` | Account hiện tại từ Gateway identity |
| `GET` | `/api/v1/users?query={text}&limit=20` | Query tên/email; `limit` 1–100 | `200 UserCandidate[]`: `id`, `fullName` | Caller `ACTIVE`; chỉ trả Account `ACTIVE` không mang `SYSTEM_ADMIN`, không trả email |
| `GET` | `/api/v1/users/profiles?projectId={id}&ids={uuid,...}` | `projectId` dương; tối đa 100 UUID | `200 UserProfile[]`: `id`, `displayName` | Caller `ACTIVE` và là Member/Owner `ACTIVE` của Project; `403` nếu không phải Member; `503` nếu Board không xác minh được |
| `POST` | `/api/v1/admin/accounts` | `{ "email": "...", "password": "...", "fullName": "..." }` | `201 UserResponse` gồm `id`, `email`, `fullName`, `status`, `authVersion`, `roles` (`["USER"]`) | Runtime; chỉ `SYSTEM_ADMIN`; `roles` không thuộc request |
| `PATCH` | `/api/v1/admin/users/{userId}/status` | `{ "status": "SUSPENDED" }` | `204 No Content` | Runtime; chỉ `SYSTEM_ADMIN` |
| `GET` | `/api/v1/admin/users/{userId}/roles` | Không body | `200 string[]` | Runtime; chỉ `SYSTEM_ADMIN` |
| `POST` | `/api/v1/admin/users/{userId}/roles` | `{ "role": "SYSTEM_ADMIN" }` | `204 No Content` | Runtime; chỉ `SYSTEM_ADMIN` |
| `PUT` | `/api/v1/admin/users/{userId}/roles` | `{ "roles": ["USER", "SYSTEM_ADMIN"] }` | `204 No Content` | Runtime; chỉ `SYSTEM_ADMIN` |
| `DELETE` | `/api/v1/admin/users/{userId}/roles/{roleCode}` | Không body | `204 No Content` | Runtime; chỉ `SYSTEM_ADMIN` |

Runtime `UserCandidate` gồm `id`, `fullName`; email chỉ được dùng để tìm kiếm, không có trong response. Runtime `UserProfile` gồm `id`, `displayName` và chỉ được trả sau khi Board xác minh caller là Member/Owner `ACTIVE` của Project đang `ACTIVE`. Account tombstone chưa thuộc Kỳ 1. Nếu Board chưa triển khai endpoint membership nội bộ hoặc không thể xác minh request, profile request trả `503 AUTHORIZATION_UNAVAILABLE`.

### Account lookup nội bộ (`GET /internal/users/{userId}`)

Board-service dùng route này để xác minh Account khi mời thành viên. Request phải có `X-Service-Token` trùng với `INTERNAL_SERVICE_TOKEN` cấu hình ở user-service và service gọi; thiếu token hoặc sai token trả `401`, Account không tồn tại trả `404 ACCOUNT_NOT_FOUND`. Thành công trả `200 { "status": "ACTIVE" }` (hoặc trạng thái hiện tại như `SUSPENDED`, `LOCKED`, `INACTIVE`). Route chỉ trả status, không trả email hay tên; token chưa cấu hình thì route từ chối tất cả request.

### Tạo Account qua System Admin (`POST /api/v1/admin/accounts`)

- Request bắt buộc `email`, `password`, `fullName`; không khai báo trường `roles`. Tài khoản mới có trạng thái `ACTIVE` và role `USER`.
- `email` phải hợp lệ, không rỗng và tối đa 255 ký tự; `fullName` không rỗng và tối đa 255 ký tự. `password` dài 6–100 ký tự, đồng thời tối đa 72 byte UTF-8 theo giới hạn BCrypt.
- Response `201 Created` trả `id`, `email`, `fullName`, `status`, `roles`, `authVersion`; không trả plaintext password hoặc hash.
- Lỗi: `400 VALIDATION_FAILED`, `401 INVALID_CREDENTIALS`, `403 SYSTEM_ADMIN_REQUIRED`, `409 EMAIL_ALREADY_EXISTS`. Email race chỉ được nhận diện khi DB báo constraint `uq_users_email`; integrity error khác trả `500 INTERNAL_SERVER_ERROR`.
- Việc System Admin đặt mật khẩu ban đầu là lựa chọn Kỳ 1; Kỳ 2 sẽ đánh giá link dùng một lần hoặc yêu cầu đổi mật khẩu lần đầu.

Ví dụ response directory và bulk profile (shapes đề xuất):

```json
[
  { "id": "550e8400-e29b-41d4-a716-446655440000", "fullName": "Alex Morgan", "email": "alex@example.com" }
]
```

```json
[
  { "id": "550e8400-e29b-41d4-a716-446655440000", "displayName": "Alex Morgan" }
]
```

Query search tối thiểu, case-folding, page size/max, thứ tự kết quả, giới hạn UUID mỗi batch, hành vi với UUID không biết và authorization directory còn mở.

Error contract mục tiêu (mã cụ thể là đề xuất):

| Tình huống | HTTP đề xuất | Code đề xuất |
|---|---:|---|
| Input login/provisioning sai | `400` | `VALIDATION_FAILED` |
| Credential sai | `401` | `INVALID_CREDENTIALS` |
| Account không tồn tại | `404` | `ACCOUNT_NOT_FOUND` |
| Actor không phải System Admin | `403` | `SYSTEM_ADMIN_REQUIRED` |
| Email đã được dùng | `409` | `EMAIL_ALREADY_EXISTS` |

Không trả credential/hash trong response. Runtime dùng error envelope `{code,message,timestamp,path}`. `SYSTEM_ADMIN` chỉ bảo vệ Account Admin API, không cấp quyền Project (theo quyết định #7).

`SystemRoles` và `UserSystemRoles` được map trong user_db. SQL bootstrap local seed Account System Admin mẫu; Kỳ 1 không dùng Flyway. Issue #7 đã chốt Account mang `SYSTEM_ADMIN` không làm Owner/Member Project và chỉ System Admin tạo Account; issue #54 chốt Admin create contract nêu trên. Kỳ 1 không có Account deletion API, deletion status endpoint hoặc cascade `operationId`; soft delete và Owner succession được bàn ở Kỳ 2.

Contract chung và field proposal nằm tại [Kanban API](../../../docs/api/kanban-api.md#2-contract-chi-tiết-project-first-mục-tiêu-bản-thiết-kế); các API hiện có trong controller và khả năng khởi chạy vẫn được mô tả riêng ở phần runtime phía trên.
