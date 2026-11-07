# user-service API và ranh giới triển khai

**Vai trò mục tiêu:** sở hữu Account, credential, Account status, `SystemRoles`/`UserSystemRoles` và Account authorization snapshot. Account deletion/cascade được hoãn sang Kỳ 2. Quyết định chung: [architecture decisions](../../../docs/project/architecture-decisions.md); route Project-first mục tiêu: [Kanban API](../../../docs/api/kanban-api.md).

Shared messaging của user-service: [hướng dẫn đọc và dùng](database.md#shared-modules-trong-user-service).

## Controller hiện có

`POST /api/v1/auth/register` khai báo đăng ký công khai (đã bãi bỏ), `POST /api/v1/auth/login` đăng nhập, `GET /api/v1/auth/public-key` xuất public key PEM, `GET /api/v1/users/me` lấy hồ sơ tài khoản hiện tại, `GET /api/v1/users` tìm kiếm ứng viên danh bạ, `GET /api/v1/users/profiles` lấy profile hiển thị theo danh sách UUIDs, `POST /api/v1/admin/accounts` tạo tài khoản bởi System Admin, `PATCH /api/v1/admin/users/{userId}/status` cập nhật trạng thái tài khoản, và `PUT /api/v1/admin/users/{userId}/roles` cập nhật vai trò hệ thống.

- Mapping `/auth/register` vẫn tồn tại trong controller nhưng là route cũ, đã bị bãi bỏ theo quyết định #7; ném `OPERATION_NOT_SUPPORTED`. Chính sách đã chốt là chỉ System Admin mới tạo Account.
- `/auth/login` gọi `LoginUserPort`; đã triển khai qua `LoginUserService` và `JwtTokenAdapter` sử dụng RSA256 (ký bằng Private Key, xác thực qua Public Key).
- `/users/me` xác thực token từ header `Authorization: Bearer <jwt>`, trích xuất subject UUID qua RSA256 và gọi `GetCurrentUserPort` (triển khai qua `GetCurrentUserService`); không cho phép client dùng `X-User-Id` giả mạo.
- `/admin/accounts`, `/admin/users/{userId}/status`, `/admin/users/{userId}/roles` được quản trị bởi `AdminUserController`, yêu cầu Bearer token của tài khoản mang role `SYSTEM_ADMIN`. Thao tác cập nhật trạng thái/vai trò đảm bảo tính nguyên tử: tăng `auth_version`, ghi Outbox và Snapshot sync job trong cùng transaction.
- `/users` và `/users/profiles` phục vụ Project Owner tìm kiếm thành viên và Frontend render tên hiển thị của Card/Member mà không làm lộ email hay dữ liệu nhạy cảm.


## Thiết kế mục tiêu đã thống nhất

- System Admin quản lý vòng đời Account; Admin không có quyền nghiệp vụ trong Project.
- `SYSTEM_ADMIN` là quyền hệ thống, không phải Project role. Account mang role này không được tạo Project hoặc làm Owner/Member. Nếu cùng một cá nhân cần tham gia Project, dùng Account nghiệp vụ khác không mang `SYSTEM_ADMIN`.
- Owner duy nhất của Project không bị biến thành “admin hệ thống”. Account được mời chỉ thành Member sau khi chấp nhận Invitation.
- Owner cần chọn User đã có tài khoản để mời; user-service là nguồn chuẩn Account nên API directory/profile mục tiêu thuộc service này. Hai route dưới đây là đề xuất, chưa có controller.
- Schema mục tiêu lưu system role bằng `SystemRoles` và quan hệ `UserSystemRoles`. SQL bootstrap local seed Account System Admin mẫu và role tương ứng trong `user_db`; Kỳ 1 không dùng Flyway. Cách bootstrap cho môi trường triển khai có thể được thiết kế sau Kỳ 2.
- Khi Account status hoặc system role đổi, tăng `auth_version`, ghi Outbox event và durable snapshot-sync job cùng transaction trong user_db.
- user-service sở hữu Account authentication snapshot và retry apply vào Redis theo version. Snapshot version cũ không được ghi đè version mới.
- Account deletion, `DELETING`, deletion operation và tombstone không thuộc Kỳ 1; giữ soft delete/Owner succession cho Kỳ 2.

## Chưa triển khai hoặc cần chốt

- Login, provisioning Account qua Admin và phát token chưa đủ để coi Auth hoàn chỉnh.
- Entity/repository `SystemRoles`/`UserSystemRoles` và Account snapshot recorder/Redis worker đã có skeleton; use case/API tạo Account qua Admin và quản lý role cần triển khai. SQL bootstrap local đã có Account System Admin mẫu; Kỳ 1 không dùng Flyway.
- Recorder chưa được nối vào các luồng tạo/thay đổi Account để ghi snapshot job cùng transaction; Redis worker chỉ chạy khi bật property. Vì vậy Account snapshot sync chưa hoạt động end-to-end. Gateway auth và Directory/profile controller cũng chưa có.
- Issue #7 đã chốt Account mang `SYSTEM_ADMIN` không được làm Owner/Member trong Project, chỉ System Admin tạo Account và không public self-registration. Nếu cùng một cá nhân cần làm việc trong Project, dùng Account nghiệp vụ khác không mang `SYSTEM_ADMIN`. Directory privacy và Account validation từ board-service vẫn còn mở.
- Xem [ghi chú Account deletion Kỳ 2](../../../docs/project/account-deletion-skeleton-guide.md); không dùng nó như hướng dẫn triển khai Kỳ 1.

## Nguồn code

`UserController`, Account/role persistence cùng snapshot recorder/worker skeleton nằm trong `services/user-service/src/main/java`. Snapshot sync chưa được nối với lifecycle Account; Account deletion classes thuộc phạm vi Kỳ 2 và không nằm trong runtime Kỳ 1.

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

`email` phải đúng định dạng email và không rỗng; `password` dài 6–100 ký tự; `fullName` không rỗng. Response không bao giờ gồm password/hash:

```json
{"id":"550e8400-e29b-41d4-a716-446655440000","email":"alex@example.com","fullName":"Alex Morgan"}
```

| Field | Kiểu JSON | Bắt buộc/validation | Ghi chú |
|---|---|---|---|
| `email` | string | Có; `@NotBlank`, `@Email` | Không có quy tắc chuẩn hóa chữ hoa/thường được khai báo ở DTO. |
| `password` | string | Có; `@NotBlank`, 6–100 ký tự | Chỉ nhận plaintext ở request; không trả password/hash trong response. |
| `fullName` | string | Có; `@NotBlank` | DTO không đặt giới hạn độ dài; cột JPA hiện là varchar(255). |

`UserResponse` có `id` (UUID), `email` (string), `fullName` (string). Status thành công là `201 Created`.

### Đăng nhập

Request gồm `email` hợp lệ và `password` không rỗng. Response:

```json
{"accessToken":"<token returned by login port>","tokenType":"Bearer"}
```

Source hiện chưa định nghĩa expiry, refresh token hay JWT claims trong response contract.

| Field | Kiểu JSON | Bắt buộc/validation |
|---|---|---|
| `email` | string | Có; `@NotBlank`, `@Email` |
| `password` | string | Có; `@NotBlank`; DTO không đặt giới hạn độ dài |

Response `TokenResponse` có `accessToken` (string) và `tokenType` (string, hiện luôn là `Bearer`); status thành công là `200 OK`.

### Hồ sơ hiện tại

Controller xác thực danh tính trực tiếp từ header `Authorization: Bearer <token>`. Adapter `JwtTokenAdapter` xác thực tính hợp lệ của chữ ký RSA256, hạn sử dụng và trích xuất subject `UUID`. Client không thể truyền header hoặc parameter giả mạo để thay đổi tài khoản được đọc.

Response `UserResponse` có `id` (UUID), `email` (string), `fullName` (string), status thành công là `200 OK`. Không trả về mật khẩu, hash hay dữ liệu nhạy cảm.

*Ghi chú về ranh giới xác thực:* Theo quyết định kiến trúc (`architecture-decisions.md` mục 6), API Gateway sẽ là ranh giới xác thực chính và forward identity context tin cậy cho service phía sau. Việc `UserController.me()` tự xác thực Bearer token qua RSA256 hiện tại là giải pháp phòng vệ tạm thời khi service còn truy cập trực tiếp độc lập mà chưa có Gateway bảo vệ (Issue #34). Endpoint `GET /api/v1/auth/public-key` cung cấp Public Key cho Gateway để Gateway thực hiện xác thực tập trung.

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

User-service sở hữu Account/Auth và system role. Các route dưới đây là bản thiết kế, không phải controller runtime:

`POST /api/v1/auth/register` hiện có trong mapping runtime nhưng không thuộc target Kỳ 1: không public self-registration, chỉ System Admin được tạo Account. Route/payload và cách cấp credential cho API Admin vẫn còn mở; không suy ra chi tiết contract từ controller cũ.

| Method | Route mục tiêu | Request | Response | Quyền |
|---|---|---|---|---|
| `POST` | `/api/v1/auth/login` | `{ "email": "...", "password": "..." }` | `200 TokenResponse` | Public; chỉ Account `ACTIVE` |
| `GET` | `/api/v1/users/me` | Không body | `200 UserSummary` | Account hiện tại từ Gateway identity |
| `GET` | `/api/v1/users?query={text}&limit=20` | Query tên/email | `200 UserCandidate[]` (đề xuất) | Account đã xác thực; chỉ tìm Account `ACTIVE`; quyền directory/privacy và giới hạn cần chốt |
| `GET` | `/api/v1/users/profiles?ids={uuid,...}` | UUID list | `200 UserProfile[]` (đề xuất) | Account đã xác thực; batch profile tối thiểu |
| `POST` | `/api/v1/admin/accounts` | Payload chưa chốt | `201 AccountSummary` (đề xuất) | System Admin |

`UserCandidate` đề xuất gồm `id`, `fullName`, `email`; email chỉ được trả trong luồng chọn người mời nếu quyền tra cứu directory được duyệt. `UserProfile` đề xuất gồm `id`, `displayName`. Account tombstone chưa thuộc Kỳ 1. Để phân biệt người còn trong Project, client đối chiếu profile với Membership do board-service trả; cách compose và quyền profile lookup còn mở.

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

Không trả credential/hash trong response. Error envelope đề xuất là `{code,message,timestamp,path}`; route/payload Admin, status operation, error mapping cuối, System Admin role storage/cấp-thu hồi và credential provisioning còn mở. `SYSTEM_ADMIN` chỉ bảo vệ Account Admin API, không cấp quyền Project (theo quyết định #7).

`SystemRoles` và `UserSystemRoles` là schema mục tiêu của user_db. SQL bootstrap local seed Account System Admin mẫu; Kỳ 1 không dùng Flyway. Issue #7 đã chốt Account mang `SYSTEM_ADMIN` không làm Owner/Member Project và chỉ System Admin tạo Account, không public self-registration. Route cấp/thu hồi role, Admin account API contract và credential provisioning còn mở. Kỳ 1 không có Account deletion API, deletion status endpoint hoặc cascade `operationId`; soft delete và Owner succession được bàn ở Kỳ 2.

Contract chung và field proposal nằm tại [Kanban API](../../../docs/api/kanban-api.md#2-contract-chi-tiết-project-first-mục-tiêu-bản-thiết-kế); các API hiện có trong controller và khả năng khởi chạy vẫn được mô tả riêng ở phần runtime phía trên.
