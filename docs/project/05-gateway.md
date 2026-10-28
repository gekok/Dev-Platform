# 05 — API Gateway: xác thực danh tính và định tuyến

Tài liệu nội bộ route/configuration: [Gateway README](../../gateway/README.md) và [routing reference](../../gateway/docs/routing.md).

**Vai trò mục tiêu:** Gateway là cổng HTTP cho Web/Mobile, xác thực Account và chuyển tiếp identity context tin cậy; không sở hữu Project authorization snapshot.

**Tham chiếu:** [architecture decisions](architecture-decisions.md), [API Kỳ 1](../api/kanban-api.md), [gateway config hiện tại](../../gateway/src/main/resources/application.yml).

## Runtime đã đối chiếu

- Gateway hiện route **/api/v1/auth/** và **/api/v1/users/** sang user-service.
- Route Card **/api/v1/boards/**/cards** và **/api/v1/cards/** đứng trước route tổng **/api/v1/boards/** sang board-service; **/ws/** sang task-service; notifications sang notification-service.
- Cấu hình route không có bằng chứng Gateway đang xác thực JWT hoặc xóa header identity giả mạo.
- RabbitMQ có trong Compose; route HTTP không đồng nghĩa service event consumer đã kết nối.

## Thiết kế đích

- Route Project, Invitation, Membership, Board và Column tới board-service; Card/Comment tới task-service; Account/Auth tới user-service; Notification tới notification-service.
- User directory search và bulk profile routes mục tiêu (`/api/v1/users?query=...`, `/api/v1/users/profiles?ids=...`) tới user-service; không gửi credential hoặc profile dư thừa cho UI.
- Giữ route Card-specific trước route Board tổng khi cùng prefix có thể match.
- Xác thực token tại Gateway và truyền identity context theo cơ chế nội bộ đã chọn; service tin Gateway theo boundary đã duyệt.
- Loại bỏ/ghi đè identity header do client tự gửi; không để truy cập trực tiếp service trở thành đường bypass Gateway.
- Admin chỉ quản trị Account; không tự có quyền trong Project.
- Internal REST giữa service chưa triển khai do chưa có bảo mật nội bộ. Cơ chế header/trust, direct-service restrictions và identity propagation chi tiết còn cần chốt khi triển khai security.

## Việc cần làm

- [ ] Chốt JWT issuer/signature/expiry, role claim cho System Admin và identity forwarding contract.
- [ ] Cập nhật routes cho Project-first API: route exact `/api/v1/users/me/project-invitations` tới board-service phải đứng trước `/api/v1/users/**`; Project/Board tới board-service; Card tới task-service; Admin tới user-service. Không biến route map thành xác nhận API đã implement.
- [ ] Route User directory/profile tới user-service; bảo đảm invitation inbox exact route tiếp tục được match trước route `/users/**` tổng.
- [ ] Bảo đảm route order cho invitation inbox, **/projects/**, **/boards/{id}/cards**, **/cards/**, **/boards/**, **/admin/**, notifications và WebSocket.
- [ ] Chốt cách chặn client giả **X-User-Id** và truy cập trực tiếp service.
- [ ] Tích hợp Admin Account API sau khi route/payload được chốt; Admin không được bypass Project permissions.
- [ ] Nếu Gateway dùng Redis Authorization Snapshot cho Project access, kiểm tra revocation/fence trước khi cho request mới qua; không dùng snapshot cũ khi cache lỗi hoặc version không xác minh được.
- [ ] Chạy API/integration checks qua Gateway sau khi service contracts và auth wiring hoàn thành.
