# 01 — user-service: Account, Auth và System Roles

**Sở hữu mục tiêu:** Account, credential, trạng thái Account, `SystemRoles`/`UserSystemRoles` và Account authorization snapshot trong **user_db**. Account deletion được hoãn sang Kỳ 2.

**Hợp đồng:** [Kanban API](../api/kanban-api.md), [quyết định kiến trúc](architecture-decisions.md), [API/database riêng](../../services/user-service/docs/api.md), [ghi chú Account deletion Kỳ 2](account-deletion-skeleton-guide.md).

## Runtime đã đối chiếu

- **UserController** khai báo **POST /api/v1/auth/register**, **POST /api/v1/auth/login** và **GET /api/v1/users/me**.
- **/users/me** xác thực token JWT qua RSA256, gọi **GetCurrentUserService** và từ chối client giả mạo identity; **LoginUserPort** đã được triển khai qua **LoginUserService** và **JwtTokenAdapter** (cặp khóa bất đối xứng RSA256).
- Public registration là route cũ; theo quyết định #7, chính sách đã chốt chỉ System Admin mới được tạo Account, loại bỏ public registration.
- JPA mapping cho `SystemRoles`/`UserSystemRoles`, `auth_version`, snapshot-sync job/recorder và Redis writer/worker đã có trong source. Admin create/status/role mutation ghi Outbox và snapshot-sync job cùng transaction; worker mặc định tắt. Gateway JWT filter xác thực token, đọc Account snapshot Redis và ghi đè `X-User-Id`; khi snapshot chưa được apply, request bảo vệ trả `503`. Direct-service ingress chưa bị chặn. Account deletion operation không thuộc runtime Kỳ 1.
- `SYSTEM_ADMIN` chỉ dùng cho quản trị Account; schema mục tiêu dùng `SystemRoles`/`UserSystemRoles`. Account mang role này không được làm Owner/Member trong Project. Nếu cùng một cá nhân cần tham gia Project, dùng Account nghiệp vụ không mang `SYSTEM_ADMIN`. SQL bootstrap local seed Account System Admin mẫu; Kỳ 1 không dùng Flyway.

## Việc cần làm

- [x] Chốt chính sách cấp Account: chỉ System Admin được tạo Account, không mở public signup (issue #7).
- [x] Chốt route, payload và cách cấp credential cho API tạo Account qua System Admin (POST /api/v1/admin/accounts, PATCH /status, PUT /roles).
- [x] Thêm JPA entity/repository target cho `SystemRoles`/`UserSystemRoles` trong user_db; role mutation API/authorization vẫn cần triển khai, không nhận role từ payload client và không dùng quyền Admin làm quyền Project.
- [x] Bổ sung API directory search để Owner chọn User đã tồn tại và bulk profile để UI render Member/author; chỉ trả dữ liệu tối thiểu và chỉ tìm Account ACTIVE theo policy được duyệt.
- [x] Implement login, token issuance/validation và current-user từ identity đáng tin cậy; bỏ identity stub do client tự gửi (đã giải quyết tại issue #12 và #13).
- [x] Thêm `auth_version` persistence và snapshot-sync job/recorder seam; cần nối Account/role mutation, Outbox và job trong cùng transaction.
- [x] Thêm retry worker/Redis writer theo version; worker mặc định tắt và cần bật sau khi mutation flow được nối.
- [ ] Không triển khai Account deletion, `DELETING`, deletion operation hay tombstone trong Kỳ 1; giữ định hướng soft delete/Owner succession ở ghi chú Kỳ 2.
- [x] Cập nhật [API docs riêng](../../services/user-service/docs/api.md) và [database docs riêng](../../services/user-service/docs/database.md) khi field/contract thay đổi.

## Tiêu chí nghiệm thu khi triển khai

- System Admin được tách khỏi Project permissions; Owner không có quyền quản trị hệ thống.
- Tạo Project chỉ dựa trên Account nghiệp vụ `ACTIVE` đã xác thực tại Gateway; Account mang `SYSTEM_ADMIN` không được tạo Project hoặc tham gia Project (theo quyết định #7).
- `SYSTEM_ADMIN` được lưu qua `SystemRoles`/`UserSystemRoles`; SQL bootstrap local seed Account System Admin mẫu, không dùng Flyway trong Kỳ 1. Nếu một cá nhân cần làm việc trong Project, họ dùng Account nghiệp vụ khác không mang `SYSTEM_ADMIN`.
- Mọi thay đổi Account/status/role tăng `auth_version` và tạo Outbox cùng snapshot-sync job nguyên tử.
- API/service không coi token chưa hết hạn là bằng chứng Account còn ACTIVE; Task authorization fail closed khi snapshot Redis thiếu/lỗi/không xác minh được.
- Account deletion không có trong Kỳ 1; không có endpoint hoặc runtime cascade để nghiệm thu.
