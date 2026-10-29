# user-service API và ranh giới triển khai

**Vai trò mục tiêu:** sở hữu Account, credential, Account status, `SystemRoles`/`UserSystemRoles` và Account authorization snapshot. Account deletion/cascade được hoãn sang Kỳ 2. Quyết định chung: [architecture decisions](../../../docs/project/architecture-decisions.md); route Project-first mục tiêu: [Kanban API](../../../docs/api/kanban-api.md).

Shared messaging của user-service: [hướng dẫn đọc và dùng](database.md#shared-modules-trong-user-service).

## Controller hiện có

`POST /api/v1/auth/register` khai báo đăng ký công khai, `POST /api/v1/auth/login` khai báo đăng nhập, `GET /api/v1/users/me` khai báo lấy hồ sơ. Đây là các route được `UserController` ánh xạ; route declaration không chứng minh use case đã chạy hoàn chỉnh.

- `/auth/register` nhận `email`, `password`, `fullName` và gọi `RegisterUserPort`.
- `/auth/login` gọi `LoginUserPort`; hiện chưa có implementation của port này.
- `/users/me` hiện dùng `X-User-Id` để lấy Account; `Authorization` được đọc nhưng JWT chưa được parse.
- Repo chưa có route Admin tạo Account. Chính sách public registration và route tạo Account còn mở; route register cũ không chứng minh nghiệp vụ mục tiêu đã chốt.

## Thiết kế mục tiêu đã thống nhất

- System Admin quản lý vòng đời Account; Admin không có quyền nghiệp vụ trong Project.
- `SYSTEM_ADMIN` là quyền hệ thống, không phải Project role. Chỉ quyền này chưa cho phép tạo/thao tác Project; quy tắc Account đồng thời là Admin và người dùng Project còn mở.
- Owner duy nhất của Project không bị biến thành “admin hệ thống”. Account được mời chỉ thành Member sau khi chấp nhận Invitation.
- Owner cần chọn User đã có tài khoản để mời; user-service là nguồn chuẩn Account nên API directory/profile mục tiêu thuộc service này. Hai route dưới đây là đề xuất, chưa có controller.
- Schema mục tiêu lưu system role bằng `SystemRoles` và quan hệ `UserSystemRoles`; bootstrap System Admin đầu tiên còn mở.
- Khi Account status hoặc system role đổi, tăng `auth_version`, ghi Outbox event và durable snapshot-sync job cùng transaction trong user_db.
- user-service sở hữu Account authentication snapshot và retry apply vào Redis theo version. Snapshot version cũ không được ghi đè version mới.
- Account deletion, `DELETING`, deletion operation và tombstone không thuộc Kỳ 1; giữ soft delete/Owner succession cho Kỳ 2.

## Chưa triển khai hoặc cần chốt

- Login, đăng ký/provisioning Account và phát token chưa đủ để coi Auth hoàn chỉnh.
- Entity/repository `SystemRoles`/`UserSystemRoles` và Account snapshot recorder/Redis worker đã có skeleton; chưa có use case/API quản lý role hoặc bootstrap System Admin.
- Recorder chưa được nối vào các luồng tạo/thay đổi Account để ghi snapshot job cùng transaction; Redis worker chỉ chạy khi bật property. Vì vậy Account snapshot sync chưa hoạt động end-to-end. Gateway auth và Directory/profile controller cũng chưa có.
- Bootstrap Admin đầu tiên, chính sách đa vai trò, directory privacy và Account validation từ board-service còn mở.
- Xem [ghi chú Account deletion Kỳ 2](../../../docs/project/account-deletion-skeleton-guide.md); không dùng nó như hướng dẫn triển khai Kỳ 1.

## Nguồn code

`UserController`, Account/role persistence cùng snapshot recorder/worker skeleton nằm trong `services/user-service/src/main/java`. Snapshot sync chưa được nối với lifecycle Account; Account deletion classes thuộc phạm vi Kỳ 2 và không nằm trong runtime Kỳ 1.

## Chi tiết contract được khai báo trong source

Base path là `/api/v1`. Các route dưới đây là mapping của controller; trạng thái port bên dưới quyết định route có thể được khởi chạy đầy đủ hay chưa.

| Method | Path | Header/params | Request | Success |
|---|---|---|---|---|
| `POST` | `/api/v1/auth/register` | Không đọc identity header | `RegisterRequest` | `201`, `UserResponse` |
| `POST` | `/api/v1/auth/login` | Không đọc identity header | `LoginRequest` | `200`, `TokenResponse` |
| `GET` | `/api/v1/users/me` | `X-User-Id: UUID`; `Authorization` optional nhưng chưa parse | Không có body | `200`, `UserResponse` |

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

Controller hiện lấy UUID từ `X-User-Id`; header `Authorization` được nhận nhưng không dùng để xác thực. Thiếu `X-User-Id` được ném thành `IllegalArgumentException`.

Response `UserResponse` có `id` (UUID), `email` (string), `fullName` (string), status thành công là `200 OK`. `X-User-Id` nhận dạng sai UUID cũng đi qua `UUID.fromString` và được handler hiện tại ánh xạ thành `400 BAD_REQUEST`.

### Error mapping và khả năng khởi chạy

| Tình huống được handler bắt | HTTP | Body |
|---|---:|---|
| `IllegalArgumentException` | `400` | `{ "code": "BAD_REQUEST", "message": "...", "timestamp": "...", "path": "..." }` |

`GlobalExceptionHandler` chưa có handler riêng cho lỗi Bean Validation, credentials sai, trùng email, `404` hoặc lỗi xác thực. Không suy ra các body/status khác từ handler này.

`UserController` yêu cầu `RegisterUserPort`, `LoginUserPort` và `GetCurrentUserPort`; trong source hiện chỉ có adapter lưu user, chưa có bean triển khai ba inbound port này. Vì vậy các mapping trên mô tả contract được khai báo, chưa phải bằng chứng service khởi chạy và phục vụ được các request.

## API mục tiêu thuộc user-service

User-service sở hữu Account/Auth và system role. Các route dưới đây là bản thiết kế, không phải controller runtime:

`POST /api/v1/auth/register` hiện có trong mapping runtime, nhưng public self-registration chưa được chốt làm Kỳ 1 target. Route tạo Account qua System Admin cũng còn mở; không suy ra nghiệp vụ target từ controller cũ.

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

Không trả credential/hash trong response. Error envelope đề xuất là `{code,message,timestamp,path}`; route/payload Admin, status operation, error mapping cuối, System Admin role storage/bootstrap/cấp-thu hồi, chính sách một Account đa vai trò và credential provisioning còn mở. `SYSTEM_ADMIN` chỉ bảo vệ Account Admin API, không cấp quyền Project.

`SystemRoles` và `UserSystemRoles` là schema mục tiêu của user_db. Bootstrap System Admin đầu tiên, route cấp/thu hồi role và chính sách một Account đa vai trò còn mở. Kỳ 1 không có Account deletion API, deletion status endpoint hoặc cascade `operationId`; soft delete và Owner succession được bàn ở Kỳ 2.

Contract chung và field proposal nằm tại [Kanban API](../../../docs/api/kanban-api.md#2-contract-chi-tiết-project-first-mục-tiêu-bản-thiết-kế); các API hiện có trong controller và khả năng khởi chạy vẫn được mô tả riêng ở phần runtime phía trên.
