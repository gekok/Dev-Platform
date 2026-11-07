# 05 — API Gateway: xác thực danh tính và định tuyến

Tài liệu nội bộ route/configuration: [Gateway README](../../gateway/README.md) và [routing reference](../../gateway/docs/routing.md).

**Vai trò mục tiêu:** Gateway là cổng HTTP cho Web/Mobile, xác thực Account và chuyển tiếp identity context tin cậy; không sở hữu Project authorization snapshot.

**Tham chiếu:** [architecture decisions](architecture-decisions.md), [API Kỳ 1](../api/kanban-api.md), [gateway config hiện tại](../../gateway/src/main/resources/application.yml).

## Runtime đã đối chiếu

- Gateway hiện route **/api/v1/auth/** và **/api/v1/users/** sang user-service; **/api/v1/admin/** chưa có route. Source có route `/ws/**` tới task-service nhưng đang khai báo HTTP upstream, cần xác nhận/chuyển sang WebSocket upstream trong phạm vi Gateway #31.
- Route hiện tại đưa Project-scoped Card path cụ thể tới task-service trước `/api/v1/projects/**`; Board-first Board routes vẫn đi board-service.
- Gateway có WebFilter xác thực JWT RSA256 bằng public key lấy từ user-service, issuer cấu hình, thời hạn token và UUID subject; public key được cache trong process đến khi Gateway khởi khởi động lại. Mọi request không nằm trong ba route public phải có Bearer token.
- Sau khi JWT hợp lệ, Gateway đọc `auth:account:{accountId}` từ Redis. Chỉ snapshot `ACTIVE` được đi tiếp; snapshot thiếu, sai cấu trúc hoặc Redis lỗi trả `503`, Account không ACTIVE trả `403`, token thiếu/sai trả `401`. Gateway gỡ `X-User-Id` do client gửi, đặt lại theo `sub` và giữ nguyên `Authorization` khi proxy.
- Gateway không lấy Project permission từ JWT hoặc Account snapshot; service sở hữu nghiệp vụ vẫn phải kiểm tra quyền Project. `roles` và `authVersion` nằm trong JWT do user-service phát nhưng Gateway không dùng chúng để cấp quyền Project.
- Snapshot-sync worker của user-service mặc định tắt (`USER_AUTH_SNAPSHOT_SYNC_ENABLED=false`); nếu Redis chưa có snapshot hợp lệ, request bảo vệ fail-closed `503`.
- Direct-service ingress chưa bị chặn theo phạm vi đã thống nhất; không coi request gọi thẳng service là đã được Gateway bảo vệ. CORS và chính sách rotation/refresh public key còn cần cấu hình/chốt.
- RabbitMQ và Redis được cấu hình trong Compose; route HTTP không đồng nghĩa service event consumer đã kết nối.

## Thiết kế đích

- Route Project, Invitation, Membership, Board và Column tới board-service; Card/Comment tới task-service; Account/Auth tới user-service; Notification tới notification-service.
- User directory search và bulk profile routes mục tiêu (`/api/v1/users?query=...`, `/api/v1/users/profiles?ids=...`) tới user-service; không gửi credential hoặc profile dư thừa cho UI.
- Giữ route Card-specific trước route Board tổng khi cùng prefix có thể match.
- Xác thực token tại Gateway và chuyển `X-User-Id` đã xác minh; đây chỉ là boundary cho request qua Gateway, chưa thay thế authorization trong service.
- Với `WS /ws/boards/{boardId}` (#24), Gateway xác thực handshake và chuyển tiếp identity đã xác thực; Task kiểm tra Project membership từ snapshot. Gateway không tự cấp quyền Board/Project và không gửi identity header do client cung cấp.
- Gateway chỉ proxy frame WebSocket. Task gửi event Board sau commit theo kiểu best-effort; client phải refetch REST sau reconnect. Socket bị đóng khi Task không còn xác minh được Membership.
- Loại bỏ/ghi đè identity header do client tự gửi; không lấy Account ID hoặc role từ body/header do client điều khiển. Chặn truy cập trực tiếp service là điều kiện triển khai, hiện chưa thực hiện.
- Với `POST /api/v1/projects`, chỉ chuyển tiếp identity Account nghiệp vụ `ACTIVE` đã xác thực; board-service dùng identity làm Owner và tạo Project + Owner membership cùng transaction.
- Account phải ở trạng thái `ACTIVE`; Kỳ 1 không triển khai Account deletion/status flow. Gateway không sở hữu authorization snapshot; nguồn Account snapshot thuộc user-service và Project/Membership snapshot thuộc board-service.
- Admin chỉ quản trị Account; không tự có quyền trong Project.
- Internal REST giữa service chưa triển khai do chưa có bảo mật nội bộ. Gateway identity forwarding đã có cho HTTP request; direct-service restriction, CORS và refresh public key sau rotation còn mở.

## Việc cần làm

- [x] JWT RSA256, issuer, expiry, `iat`, UUID `sub`, snapshot Account và identity forwarding qua `X-User-Id` đã có trong source; Gateway không tin role claim để cấp quyền Project.
- [ ] Chốt cách refresh public key khi rotation và kiểm chứng snapshot-sync worker được bật trong môi trường chạy.
- [x] Đồng bộ với user-service chính sách issue #7: chỉ System Admin tạo Account, không public self-registration; Account mang `SYSTEM_ADMIN` không được làm Owner/Member trong Project. Người cần làm việc trong Project dùng Account nghiệp vụ không mang role này. SQL bootstrap local seed Admin mẫu; Kỳ 1 không dùng Flyway.
- [ ] Route `/api/v1/projects/invitations` và Project resources tới board-service; không đặt inbox Invitation trong namespace `/users`.
- [ ] Đặt Card route cụ thể `/api/v1/projects/*/boards/*/cards/**` và `/api/v1/projects/*/cards/**` tới task-service trước route board-service `/api/v1/projects/**,/api/v1/boards/**`.
- [ ] Route User directory/profile tới user-service; route notification và WebSocket tiếp tục tới service tương ứng.
- [ ] Nối auth/identity propagation cho WebSocket handshake; kiểm thử proxy qua Gateway với Task thật khi #34 và Board-to-Project lookup đã sẵn sàng.
- [x] Gateway xóa `X-User-Id` client gửi và thay bằng UUID `sub` đã xác minh.
- [ ] Chặn truy cập trực tiếp service trước khi bật trusted Gateway identity ở các service con; việc chặn ingress hiện được hoãn.
- [ ] Không thêm Account deletion/status route trong Kỳ 1. Admin quản lý Account nhưng không được bypass Project permissions.
- [ ] Gateway chỉ xác thực/định tuyến và chuyển tiếp identity tin cậy; không sở hữu hoặc tự tạo bản sao Authorization Snapshot.
- [ ] Chạy API/integration checks qua Gateway sau khi service contracts và auth wiring hoàn thành.
