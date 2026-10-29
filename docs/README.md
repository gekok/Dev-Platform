# Chỉ mục tài liệu Dev-Platform

Trang này phân biệt tài liệu thiết kế mục tiêu với tài liệu mô tả runtime hiện tại. Nếu có nội dung mâu thuẫn, dùng thứ tự ưu tiên dưới đây.

## Thứ tự ưu tiên

1. [Architecture decisions](project/architecture-decisions.md): quyết định backend đã thống nhất và các mục còn mở. Đây là nguồn chuẩn cho thiết kế mục tiêu Project-first Kỳ 1.
2. [Kanban API](api/kanban-api.md): bản thiết kế route/payload/response Project-first, phân biệt phần đã chốt với đề xuất/còn mở; API Board-first cũ để tham khảo runtime.
3. [Integration checklist](project/06-integration.md): tiêu chí tích hợp mục tiêu sau khi các service được cập nhật theo kiến trúc và API mới.
4. [Account deletion notes](project/account-deletion-skeleton-guide.md): ghi chú tham khảo cho Kỳ 2; không phải hướng dẫn chạy tính năng Kỳ 1.
5. Source code và các phần runtime trong tài liệu service-specific: bằng chứng về hành vi hiện có; chúng không tự thay thế quyết định mục tiêu ở mục 1.

## Tài liệu chung

| Tài liệu | Vai trò | Trạng thái |
|---|---|---|
| `README.md` | Giới thiệu sản phẩm, topology và hiện trạng repo | Mô tả mục tiêu mới; backend còn Board-first và Auth chưa hoàn chỉnh |
| `docs/project/architecture-decisions.md` | Nguồn quyết định kiến trúc | Project-first; các mục được liệt kê là cần bàn tiếp chưa phải contract |
| `docs/api/kanban-api.md` | API mục tiêu và tham chiếu API cũ | Có request/response draft theo service; mục đề xuất/còn mở chưa phải OpenAPI đã duyệt |
| `docs/project/06-integration.md` | Kịch bản tích hợp liên service | Checklist mục tiêu, chưa phải báo cáo các flow đã chạy |
| `docs/project/account-deletion-skeleton-guide.md` | Ghi chú Account deletion/cascade | Tham khảo Kỳ 2; runtime Kỳ 1 không có luồng này |

## Tài liệu theo service

- `docs/project/01-user-service.md` đến `docs/project/05-gateway.md` là task sheets triển khai theo ownership Project-first. Các việc chưa có checkbox hoàn thành là TODO triển khai, không phải tính năng đã chạy.
- `services/{user,board,task,notification}-service/docs/` tách API/JPA runtime hiện tại khỏi API và logical schema Project-first mục tiêu. Các trang database có field/constraint/index thiết kế để lấp schema gap; chúng chưa phải migration hay persistence đã chạy. Ví dụ: board-service hiện vẫn Board-first, chưa có Project/Membership entity; task-service đã có Card route Project-scoped và Redis guard seam nhưng chưa tích hợp E2E.
- Khi docs service khác architecture/API chung, architecture decisions là nguồn quyết định; kanban-api là route mục tiêu; docs service là bằng chứng về code/schema thực tế. Phần còn mở phải được giữ nguyên là chưa chốt.

### Tài liệu nội bộ từng module

| Module | Hướng dẫn đọc | API/routing | Database |
|---|---|---|---|
| `user-service` | [API và ownership](../services/user-service/docs/api.md) | [API hiện tại và route mục tiêu](../services/user-service/docs/api.md) | [user_db](../services/user-service/docs/database.md) |
| `board-service` | [API và ownership](../services/board-service/docs/api.md) | [Board-first runtime / Project-first target](../services/board-service/docs/api.md) | [board_db và target ERD](../services/board-service/docs/database.md) |
| `task-service` | [API và ownership](../services/task-service/docs/api.md) | [Card API và WebSocket status](../services/task-service/docs/api.md) | [task_db và target ownership](../services/task-service/docs/database.md) |
| `notification-service` | [API và ownership](../services/notification-service/docs/api.md) | [Client/internal API](../services/notification-service/docs/api.md) | [notification_db](../services/notification-service/docs/database.md) |
| `shared` | [Module roles, config và local usage](../shared/README.md) | [Event contracts / Outbox / Inbox](../shared/README.md) | Mapping được lưu trong DB riêng của từng service |
| `gateway` | [Gateway README](../gateway/README.md) | [Routing reference](../gateway/docs/routing.md) | Không sở hữu DB |

## Tài liệu tham khảo và tài liệu lịch sử

- `docs/superpowers/specs/2026-10-21-kanban-web-demo-design.md` mô tả React/MSW demo. Phần topology backend và phạm vi quyền trong spec đã được quyết định kiến trúc mới thay thế; phần UI/mock vẫn là tham khảo trong đúng phạm vi demo.
- `web-demo/docs/superpowers/specs/2026-10-21-project-board-navigation-design.md` và `web-demo/src/mocks/handlers/` mô tả UX cùng mock HTTP API. Đây không phải bằng chứng backend đã implement các route/payload đó.
- `docs/superpowers/plans/` lưu kế hoạch triển khai theo thời điểm; plan không phải nguồn quyết định kiến trúc hay báo cáo runtime nếu chưa đối chiếu code.

## Thứ tự đồng bộ tài liệu

1. Dùng architecture decisions để đọc quyết định đã thống nhất và các mục còn mở.
2. Dùng kanban-api để đọc contract HTTP mục tiêu; nội dung gắn đề xuất/còn mở chưa phải contract được duyệt.
3. Dùng database docs của service để đọc logical schema mục tiêu và mapping code hiện tại; không nhầm logical design với migration/runtime.
4. Đối chiếu source code để biết route/schema nào đang chạy, rồi dùng task sheets và integration checklist để triển khai sau khi contract cần thiết được chốt.

## Trạng thái đồng bộ hiện tại

- README, API chung, task sheets 01–06, API/database docs của bốn service, `shared/README.md` và tài liệu Gateway được đồng bộ theo mô hình Project-first. Common API có contract draft; bốn database docs có logical schema per service. Đây là thiết kế tài liệu, không phải backend đã implement hay toàn bộ contract đã duyệt.
- Account deletion và Project cascade được hoãn sang Kỳ 2; [ghi chú Account deletion](project/account-deletion-skeleton-guide.md) chỉ lưu quyết định định hướng, không hướng dẫn bật scaffold trong Kỳ 1.
- React demo dùng MSW và mobile client chưa phải bằng chứng backend Project-first đã tích hợp. Các plan/spec giữ vai trò lịch sử hoặc tham khảo theo phạm vi ghi tại mục trên.
- Các mục còn mở gồm bootstrap System Admin đầu tiên và chính sách một Account đa vai trò; nguồn xác minh Account ACTIVE khi mời/Accept do internal REST chưa bảo mật; Invitation expiry/cancel; Project/Board transition graph; chi tiết cursor/error contract; retry/DLQ; Board preference ownership/storage. Redis snapshot apply là mốc hiệu lực thu hồi quyền; implementation seams và integration TODO được mô tả trong tài liệu service.
