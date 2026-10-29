# 05 — API Gateway: xác thực danh tính và định tuyến

Tài liệu nội bộ route/configuration: [Gateway README](../../gateway/README.md) và [routing reference](../../gateway/docs/routing.md).

**Vai trò mục tiêu:** Gateway là cổng HTTP cho Web/Mobile, xác thực Account và chuyển tiếp identity context tin cậy; không sở hữu Project authorization snapshot.

**Tham chiếu:** [architecture decisions](architecture-decisions.md), [API Kỳ 1](../api/kanban-api.md), [gateway config hiện tại](../../gateway/src/main/resources/application.yml).

## Runtime đã đối chiếu

- Gateway hiện route **/api/v1/auth/** và **/api/v1/users/** sang user-service.
- Route hiện tại đưa Project-scoped Card path cụ thể tới task-service trước `/api/v1/projects/**`; Board-first Board routes vẫn đi board-service.
- Cấu hình route không có bằng chứng Gateway đang xác thực JWT hoặc xóa header identity giả mạo.
- RabbitMQ có trong Compose; route HTTP không đồng nghĩa service event consumer đã kết nối.

## Thiết kế đích

- Route Project, Invitation, Membership, Board và Column tới board-service; Card/Comment tới task-service; Account/Auth tới user-service; Notification tới notification-service.
- User directory search và bulk profile routes mục tiêu (`/api/v1/users?query=...`, `/api/v1/users/profiles?ids=...`) tới user-service; không gửi credential hoặc profile dư thừa cho UI.
- Giữ route Card-specific trước route Board tổng khi cùng prefix có thể match.
- Xác thực token tại Gateway và truyền identity context theo cơ chế nội bộ đã chọn; service tin Gateway theo boundary đã duyệt.
- Loại bỏ/ghi đè identity header do client tự gửi; không để truy cập trực tiếp service trở thành đường bypass Gateway. Không lấy Account ID hoặc role từ body/header do client điều khiển.
- Với `POST /api/v1/projects`, chỉ chuyển tiếp identity Account nghiệp vụ `ACTIVE` đã xác thực; board-service dùng identity làm Owner và tạo Project + Owner membership cùng transaction.
- Account phải ở trạng thái `ACTIVE`; Kỳ 1 không triển khai Account deletion/status flow. Gateway không sở hữu authorization snapshot; nguồn Account snapshot thuộc user-service và Project/Membership snapshot thuộc board-service.
- Admin chỉ quản trị Account; không tự có quyền trong Project.
- Internal REST giữa service chưa triển khai do chưa có bảo mật nội bộ. Cơ chế header/trust, direct-service restrictions và identity propagation chi tiết còn cần chốt khi triển khai security.

## Việc cần làm

- [ ] Chốt JWT issuer/signature/expiry, nguồn role claim System Admin, Account status/revocation freshness và identity forwarding contract; không tin role/identity do client gửi.
- [ ] Đồng bộ với user-service cách bootstrap System Admin đầu tiên và chính sách một Account vừa có quyền Admin vừa dùng Project.
- [ ] Route `/api/v1/projects/invitations` và Project resources tới board-service; không đặt inbox Invitation trong namespace `/users`.
- [ ] Đặt Card route cụ thể `/api/v1/projects/*/boards/*/cards/**` và `/api/v1/projects/*/cards/**` tới task-service trước route board-service `/api/v1/projects/**,/api/v1/boards/**`.
- [ ] Route User directory/profile tới user-service; route notification và WebSocket tiếp tục tới service tương ứng.
- [ ] Chốt cách chặn client giả **X-User-Id** và truy cập trực tiếp service.
- [ ] Không thêm Account deletion/status route trong Kỳ 1. Admin quản lý Account nhưng không được bypass Project permissions.
- [ ] Gateway chỉ xác thực/định tuyến và chuyển tiếp identity tin cậy; không sở hữu hoặc tự tạo bản sao Authorization Snapshot.
- [ ] Chạy API/integration checks qua Gateway sau khi service contracts và auth wiring hoàn thành.
