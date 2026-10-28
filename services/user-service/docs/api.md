# user-service API và ranh giới triển khai

**Vai trò mục tiêu:** sở hữu Account, thông tin đăng nhập và trạng thái vòng đời tài khoản. Quyết định chung: [architecture decisions](../../../docs/project/architecture-decisions.md); route Project-first mục tiêu: [Kanban API](../../../docs/api/kanban-api.md).

## Controller hiện có

`POST /api/v1/auth/register` khai báo đăng ký công khai, `POST /api/v1/auth/login` khai báo đăng nhập, `GET /api/v1/users/me` khai báo lấy hồ sơ. Đây là các route được `UserController` ánh xạ; route declaration không chứng minh use case đã chạy hoàn chỉnh.

- `/auth/register` nhận `email`, `password`, `fullName` và gọi `RegisterUserPort`.
- `/auth/login` gọi `LoginUserPort`; hiện chưa có implementation của port này.
- `/users/me` hiện dùng `X-User-Id` để lấy Account; `Authorization` được đọc nhưng JWT chưa được parse.
- Repo chưa có route Admin tạo/xóa Account. Chính sách public registration, route tạo Account và route xóa Account phải theo quyết định/hợp đồng Admin; không lấy route register cũ làm bằng chứng đã chốt nghiệp vụ.

## Thiết kế mục tiêu đã thống nhất

- System Admin quản lý vòng đời Account; Admin không có quyền nghiệp vụ trong Project.
- Owner duy nhất của Project không bị biến thành “admin hệ thống”. Account được mời chỉ thành Member sau khi chấp nhận Invitation.
- Owner cần chọn User đã có tài khoản để mời; user-service là nguồn chuẩn Account nên API directory/profile mục tiêu thuộc service này. Hai route dưới đây là đề xuất, chưa có controller.
- Khi yêu cầu xóa Account được ghi nhận, user-service đặt trạng thái `DELETING`, tạo operation `operationId` và ghi `ACCOUNT_DELETION_REQUESTED` vào Outbox trong cùng transaction.
- API khởi tạo chỉ trả `202 Accepted` sau khi `DELETING` + operation + Outbox đã commit và account-wide authorization bị chặn hiệu lực. Route, status response, cách xác nhận Redis fence và lỗi chi tiết còn mở; không chờ board/task cleanup.
- user-service chỉ hoàn tất operation sau khi board-service xác nhận Project/Membership cleanup; chi tiết consumer/wiring chưa được triển khai.

## Chưa triển khai hoặc cần chốt

- Login, đăng ký/provisioning Account và phát token chưa đủ để coi Auth hoàn chỉnh.
- `AccountAccessGuard` chưa được nối vào login/controller; trạng thái `DELETING` hiện chưa chặn đăng nhập ở runtime.
- Chưa có Admin controller, authorization cho Admin, deletion status endpoint, Rabbit listener cho completion hoặc gateway auth.
- Xem [hướng dẫn cascade skeleton](../../../docs/project/account-deletion-skeleton-guide.md) để tiếp tục theo các TODO đúng service.

## Nguồn code

`UserController`, `AccountDeletionApplicationService`, `AccountDeletionCompletionHandler`, `AccountDeletionOperationEntity` và `UserEntity` trong `services/user-service/src/main/java`.

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

User-service sở hữu Account/Auth và là bên điều phối xóa Account. Các route dưới đây là bản thiết kế, không phải controller runtime:

`POST /api/v1/auth/register` hiện có trong mapping runtime, nhưng public self-registration chưa được chốt làm Kỳ 1 target. Quyết định hiện tại giao System Admin tạo/xóa Account; không suy ra từ controller cũ rằng client được tự đăng ký trong API target.

| Method | Route mục tiêu | Request | Response | Quyền |
|---|---|---|---|---|
| `POST` | `/api/v1/auth/login` | `{ "email": "...", "password": "..." }` | `200 TokenResponse` | Public; không cấp token cho account `DELETING` |
| `GET` | `/api/v1/users/me` | Không body | `200 UserSummary` | Account hiện tại từ Gateway identity |
| `GET` | `/api/v1/users?query={text}&limit=20` | Query tên/email | `200 UserCandidate[]` (đề xuất) | Account đã xác thực; chỉ tìm Account `ACTIVE`; quyền directory/privacy và giới hạn cần chốt |
| `GET` | `/api/v1/users/profiles?ids={uuid,...}` | UUID list | `200 UserProfile[]` (đề xuất) | Account đã xác thực; batch profile tối thiểu và tombstone để render attribution |
| `POST` | `/api/v1/admin/accounts` | Payload chưa chốt | `201 AccountSummary` (đề xuất) | System Admin |
| `DELETE` | `/api/v1/admin/accounts/{accountId}` | Không body | `202 {operationId,status,statusUrl}` | System Admin; chỉ trả sau khi account bị chặn auth hiệu lực, request + Outbox đã bền |
| `GET` | `/api/v1/admin/deletion-operations/{operationId}` | UUID path | `200 DeletionOperation` (đề xuất) | System Admin |

`UserCandidate` đề xuất gồm `id`, `fullName`, `email`; email chỉ được trả trong luồng mời nếu quyền tra cứu directory được duyệt. `UserProfile` đề xuất gồm `id`, `displayName`, `accountDeleted`; UUID đã xóa vẫn trả tombstone tối thiểu để Comment/history không mất attribution. Để phân biệt người còn tồn tại nhưng đã rời Project, client đối chiếu profile với danh sách Membership do board-service trả; cách compose và quyền profile lookup còn mở.

Ví dụ response directory và bulk profile (shapes đề xuất):

```json
[
  { "id": "550e8400-e29b-41d4-a716-446655440000", "fullName": "Alex Morgan", "email": "alex@example.com" }
]
```

```json
[
  { "id": "550e8400-e29b-41d4-a716-446655440000", "displayName": "Alex Morgan", "accountDeleted": false },
  { "id": "550e8400-e29b-41d4-a716-446655440001", "displayName": null, "accountDeleted": true }
]
```

Query search tối thiểu, case-folding, page size/max, thứ tự kết quả, giới hạn UUID mỗi batch, hành vi với UUID không biết và authorization directory còn mở. `accountDeleted` nói về trạng thái Account; việc User còn là Project Member được xác định riêng từ board-service.

Error contract mục tiêu (mã cụ thể là đề xuất):

| Tình huống | HTTP đề xuất | Code đề xuất |
|---|---:|---|
| Input login/provisioning sai | `400` | `VALIDATION_FAILED` |
| Credential sai | `401` | `INVALID_CREDENTIALS` |
| Account không tồn tại | `404` | `ACCOUNT_NOT_FOUND` |
| Actor không phải System Admin | `403` | `SYSTEM_ADMIN_REQUIRED` |
| Email đã được dùng | `409` | `EMAIL_ALREADY_EXISTS` |
| Không thể xác minh authorization fence khi xóa Account | `503` | `AUTHORIZATION_UNAVAILABLE` |

Không trả credential/hash trong response. Error envelope đề xuất là `{code,message,timestamp,path}`; route/payload Admin, status operation, error mapping cuối, account role storage và credential provisioning còn mở.

`operationId` theo dõi cả Project cascade và việc gỡ membership/assignment cleanup. `202` xác nhận request đã được lưu bền vững và Account không còn được auth chấp nhận; nó không có nghĩa service downstream đã hoàn tất. Status response/error mapping, route Admin chính thức, role storage, cách System Admin được provision và điểm đồng bộ Redis fence còn mở. Xóa Account Owner làm cascade Project thuộc họ; Project mà họ chỉ là Member được giữ, Membership bị revoke hiệu lực trước rồi Task unassign ở nền. Account operation chỉ `COMPLETED` sau khi cả Project cascade lẫn member assignment cleanup được xác nhận.

`GET /api/v1/admin/deletion-operations/{operationId}` là route mà `statusUrl` của DELETE trỏ tới. Response mục tiêu đề xuất gồm `operationId`, `status`, `createdAt`, `updatedAt`, `totalProjects`, `completedProjects`, `pendingProjects`, `failedProjects` và safe error summaries; chỉ System Admin được đọc. Field set, trạng thái, retention và cách hiển thị lỗi còn mở.

Contract chung và field proposal nằm tại [Kanban API](../../../docs/api/kanban-api.md#2-contract-chi-tiết-project-first-mục-tiêu-bản-thiết-kế); các API hiện có trong controller và khả năng khởi chạy vẫn được mô tả riêng ở phần runtime phía trên.
