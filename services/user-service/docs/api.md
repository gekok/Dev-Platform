# user-service API và ranh giới triển khai

**Vai trò mục tiêu:** sở hữu Account, credential, Account status, `SystemRoles`/`UserSystemRoles` và Account authorization snapshot. Account deletion/cascade được hoãn sang Kỳ 2. Quyết định chung: [architecture decisions](../../../docs/project/architecture-decisions.md); route Project-first mục tiêu: [Kanban API](../../../docs/api/kanban-api.md).

Shared messaging của user-service: [hướng dẫn đọc và dùng](database.md#shared-modules-trong-user-service).

## Controller hiện có

`POST /api/v1/auth/register` khai báo đăng ký công khai (đã bãi bỏ), `POST /api/v1/auth/login` đăng nhập, `GET /api/v1/auth/public-key` xuất public key PEM, `GET /api/v1/users/me` lấy hồ sơ tài khoản hiện tại, `GET /api/v1/users` tìm kiếm ứng viên danh bạ, `GET /api/v1/users/profiles` lấy profile hiển thị theo danh sách UUIDs, `POST /api/v1/admin/accounts` tạo tài khoản bởi System Admin, `PATCH /api/v1/admin/users/{userId}/status` cập nhật trạng thái tài khoản, cùng các route `GET`, `POST`, `PUT`, `DELETE /api/v1/admin/users/{userId}/roles...` để xem và quản lý system role.

- Mapping `/auth/register` vẫn tồn tại trong controller nhưng là route cũ, đã bị bãi bỏ theo quyết định #7; ném `OPERATION_NOT_SUPPORTED`. Chính sách đã chốt là chỉ System Admin mới tạo Account.
- `/auth/login` gọi `LoginUserPort`; đã triển khai qua `LoginUserService` và `JwtTokenAdapter` sử dụng RSA256 (ký bằng Private Key, xác thực qua Public Key).
- `/users/me` xác thực token từ header `Authorization: Bearer <jwt>`, trích xuất subject UUID qua RSA256 và gọi `GetCurrentUserPort` (triển khai qua `GetCurrentUserService`); không cho phép client dùng `X-User-Id` giả mạo.
- `/admin/accounts`, `/admin/users/{userId}/status` và các route `/admin/users/{userId}/roles...` được xử lý bởi `AdminUserController`, yêu cầu Bearer token của tài khoản mang role `SYSTEM_ADMIN`. Gateway route `/api/v1/admin/**` tới user-service và áp dụng Account snapshot gate trước khi proxy. Thao tác cập nhật trạng thái/vai trò đảm bảo tính nguyên tử: tăng `auth_version`, ghi Outbox và snapshot-sync job trong cùng transaction.
- `/users` và `/users/profiles` có handler runtime: directory trả `id`, `fullName`, `email`; bulk profile trả `id`, `displayName`. Privacy policy và giới hạn directory cần được chốt riêng; hiện không mô tả email là field ẩn.


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
- Redis snapshot-sync worker chỉ chạy khi bật `USER_AUTH_SNAPSHOT_SYNC_ENABLED=true` (mặc định `false`). Khi worker chưa chạy hoặc snapshot chưa tồn tại, Gateway fail-closed `503`; Gateway JWT filter và Directory/profile controller đã có source.
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
| `GET` | `/api/v1/users` | `Authorization: Bearer <jwt>`, query, limit | Không có body | `200`, `UserCandidateResponse[]` |
| `GET` | `/api/v1/users/profiles` | `Authorization: Bearer <jwt>`, ids | Không có body | `200`, `UserProfileResponse[]` |
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

JWT hiện có claims `iss`, `sub`, `email`, `fullName`, `roles`, `authVersion`, `iat` và `exp`; issuer mặc định là `dev-platform-user-service`, thời hạn mặc định một giờ. Không có refresh-token flow. JWT được ký bằng RSA256, không mã hóa; Gateway chỉ dùng issuer, subject và time claims để xác thực, còn trạng thái Account lấy từ Redis snapshot.

| Field | Kiểu JSON | Bắt buộc/validation |
|---|---|---|
| `email` | string | Có; `@NotBlank`, `@Email` |
| `password` | string | Có; `@NotBlank`; DTO không đặt giới hạn độ dài, adapter từ chối xác thực nếu vượt 72 byte UTF-8 |

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

`POST /api/v1/auth/register` hiện có trong mapping runtime nhưng không thuộc target Kỳ 1: không public self-registration, chỉ System Admin được tạo Account. Admin API runtime nhận payload tạo Account gồm `email`, `password`, `fullName` và `roles` (tùy chọn; mặc định role `USER`), trả `UserResponse`; status và role management cũng đã có handler. Field `roles` hiện tại trái với quyết định không để client tự khai role khi tạo Account; đây là sai khác implementation, không phải contract đã duyệt. Formal contract, error mapping và cách cấp credential vẫn cần được đồng bộ.

| Method | Route mục tiêu | Request | Response | Quyền |
|---|---|---|---|---|
| `POST` | `/api/v1/auth/login` | `{ "email": "...", "password": "..." }` | `200 TokenResponse` | Public; chỉ Account `ACTIVE` |
| `GET` | `/api/v1/users/me` | Không body | `200 UserSummary` | Account hiện tại từ Gateway identity |
| `GET` | `/api/v1/users?query={text}&limit=20` | Query tên/email; mặc định `limit=20` | Runtime `200 UserCandidate[]`: `id`, `fullName`, `email` | Account đã xác thực; privacy/query rules cần chốt |
| `GET` | `/api/v1/users/profiles?ids={uuid,...}` | UUID list; tối đa 100 IDs được xử lý | Runtime `200 UserProfile[]`: `id`, `displayName` | Account đã xác thực; tombstone chưa có trong runtime |
| `POST` | `/api/v1/admin/accounts` | `{ "email": "...", "password": "...", "fullName": "...", "roles": ["USER"] }` (`roles` tùy chọn) | `201 UserResponse` gồm `id`, `email`, `fullName`, `status`, `authVersion`, `roles` | Runtime; chỉ `SYSTEM_ADMIN` |
| `PATCH` | `/api/v1/admin/users/{userId}/status` | `{ "status": "SUSPENDED" }` | `204 No Content` | Runtime; chỉ `SYSTEM_ADMIN` |
| `GET` | `/api/v1/admin/users/{userId}/roles` | Không body | `200 string[]` | Runtime; chỉ `SYSTEM_ADMIN` |
| `POST` | `/api/v1/admin/users/{userId}/roles` | `{ "role": "SYSTEM_ADMIN" }` | `204 No Content` | Runtime; chỉ `SYSTEM_ADMIN` |
| `PUT` | `/api/v1/admin/users/{userId}/roles` | `{ "roles": ["USER", "SYSTEM_ADMIN"] }` | `204 No Content` | Runtime; chỉ `SYSTEM_ADMIN` |
| `DELETE` | `/api/v1/admin/users/{userId}/roles/{roleCode}` | Không body | `204 No Content` | Runtime; chỉ `SYSTEM_ADMIN` |

Runtime `UserCandidate` gồm `id`, `fullName`, `email`; runtime `UserProfile` gồm `id`, `displayName`. Email hiện được trả bởi directory handler; policy ai được tra cứu email còn mở. Account tombstone chưa thuộc Kỳ 1. Để phân biệt người còn trong Project, client đối chiếu profile với Membership do board-service trả; cách compose và quyền profile lookup còn mở.

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

Không trả credential/hash trong response. Error envelope đề xuất là `{code,message,timestamp,path}`; status operation, error mapping cuối và credential provisioning còn mở. Admin route/payload nêu trên là runtime hiện tại, chưa thay thế việc duyệt contract chuẩn. `SYSTEM_ADMIN` chỉ bảo vệ Account Admin API, không cấp quyền Project (theo quyết định #7).

`SystemRoles` và `UserSystemRoles` là schema mục tiêu của user_db. SQL bootstrap local seed Account System Admin mẫu; Kỳ 1 không dùng Flyway. Issue #7 đã chốt Account mang `SYSTEM_ADMIN` không làm Owner/Member Project và chỉ System Admin tạo Account, không public self-registration. Route Admin và role management có implementation source; contract chuẩn và credential provisioning cần được đồng bộ. Kỳ 1 không có Account deletion API, deletion status endpoint hoặc cascade `operationId`; soft delete và Owner succession được bàn ở Kỳ 2.

Contract chung và field proposal nằm tại [Kanban API](../../../docs/api/kanban-api.md#2-contract-chi-tiết-project-first-mục-tiêu-bản-thiết-kế); các API hiện có trong controller và khả năng khởi chạy vẫn được mô tả riêng ở phần runtime phía trên.
