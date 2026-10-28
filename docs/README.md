# Chỉ mục tài liệu Dev-Platform

Trang này phân biệt tài liệu thiết kế mục tiêu với tài liệu mô tả runtime hiện tại. Nếu có nội dung mâu thuẫn, dùng thứ tự ưu tiên dưới đây.

## Thứ tự ưu tiên

1. [Architecture decisions](project/architecture-decisions.md): quyết định backend đã thống nhất và các mục còn mở. Đây là nguồn chuẩn cho thiết kế mục tiêu Project-first Kỳ 1.
2. [Kanban API](api/kanban-api.md): route mục tiêu ở mức đã thống nhất, các contract còn mở và phần API Board-first cũ được giữ để tham khảo runtime.
3. [Integration checklist](project/06-integration.md): tiêu chí tích hợp mục tiêu sau khi các service được cập nhật theo kiến trúc và API mới.
4. [Account deletion skeleton guide](project/account-deletion-skeleton-guide.md): scaffold hiện có và các bước còn phải triển khai cho từng service.
5. Source code và các phần runtime trong tài liệu service-specific: bằng chứng về hành vi hiện có; chúng không tự thay thế quyết định mục tiêu ở mục 1.

## Tài liệu chung

| Tài liệu | Vai trò | Trạng thái |
|---|---|---|
| `README.md` | Giới thiệu sản phẩm, topology và hiện trạng repo | Mô tả mục tiêu mới; backend còn Board-first và Auth chưa hoàn chỉnh |
| `docs/project/architecture-decisions.md` | Nguồn quyết định kiến trúc | Project-first; các mục được liệt kê là cần bàn tiếp chưa phải contract |
| `docs/api/kanban-api.md` | API mục tiêu và tham chiếu API cũ | Route mục tiêu được ghi riêng; payload/error chưa chốt được đánh dấu mở |
| `docs/project/06-integration.md` | Kịch bản tích hợp liên service | Checklist mục tiêu, chưa phải báo cáo các flow đã chạy |
| `docs/project/account-deletion-skeleton-guide.md` | Hướng dẫn tiếp tục scaffold cascade delete | Ghi hiện trạng code và TODO theo từng service |

## Tài liệu theo service

- `docs/project/01-user-service.md` đến `docs/project/05-gateway.md` là task sheets triển khai theo ownership Project-first. Các việc chưa có checkbox hoàn thành là TODO triển khai, không phải tính năng đã chạy.
- `services/{user,board,task,notification}-service/docs/` tách phần code/API/schema runtime hiện tại khỏi thiết kế mục tiêu và liên kết về API/architecture chung. Ví dụ: board-service hiện vẫn Board-first, chưa có Project/Membership entity; đây là trạng thái code, không phải thay đổi quyết định kiến trúc.
- Khi docs service khác architecture/API chung, architecture decisions là nguồn quyết định; kanban-api là route mục tiêu; docs service là bằng chứng về code/schema thực tế. Phần còn mở phải được giữ nguyên là chưa chốt.

## Tài liệu tham khảo và tài liệu lịch sử

- `docs/superpowers/specs/2026-10-21-kanban-web-demo-design.md` mô tả React/MSW demo. Phần topology backend và phạm vi quyền trong spec đã được quyết định kiến trúc mới thay thế; phần UI/mock vẫn là tham khảo trong đúng phạm vi demo.
- `web-demo/docs/superpowers/specs/2026-10-21-project-board-navigation-design.md` và `web-demo/src/mocks/handlers/` mô tả UX cùng mock HTTP API. Đây không phải bằng chứng backend đã implement các route/payload đó.
- `docs/superpowers/plans/` lưu kế hoạch triển khai theo thời điểm; plan không phải nguồn quyết định kiến trúc hay báo cáo runtime nếu chưa đối chiếu code.

## Thứ tự đồng bộ tài liệu

1. Dùng architecture decisions để đọc quyết định đã thống nhất và các mục còn mở.
2. Dùng kanban-api để tra route mục tiêu; payload/error/operation/event còn mở không phải contract hoàn tất.
3. Đối chiếu docs riêng và source code để biết từng route/schema đang có tới đâu.
4. Dùng task sheets và integration checklist để triển khai/kiểm thử sau khi các hợp đồng còn mở liên quan được chốt.

## Trạng thái đồng bộ hiện tại

- README, API chung, task sheets 01–06 và docs API/database của bốn service đã được rà theo cùng mô hình Project-first, với nhãn riêng cho target và runtime.
- Skeleton cascade có persistence/use-case boundary nhưng chưa có consumer/worker/API wiring end-to-end; [hướng dẫn cascade](project/account-deletion-skeleton-guide.md) ghi chi tiết.
- React demo dùng MSW và mobile client chưa phải bằng chứng backend Project-first đã tích hợp. Các plan/spec giữ vai trò lịch sử hoặc tham khảo theo phạm vi ghi tại mục trên.
- Việc đồng bộ tài liệu không đóng các quyết định còn mở như Admin Account routes, Invitation error/lifecycle, status transitions, event response, notification cleanup scope, retry/DLQ và Board preference ownership/storage.
