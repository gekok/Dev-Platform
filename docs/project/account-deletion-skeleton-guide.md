# Ghi chú định hướng Account deletion — Kỳ 2

**Cập nhật:** 2026-10-29

Account deletion và Project cascade không thuộc runtime hoặc hợp đồng Kỳ 1. Không dùng ghi chú này để khôi phục/bật các scaffold cũ; source Account/Project deletion đã được gỡ khỏi skeleton Kỳ 1. Outbox/Inbox dùng chung và assignment cleanup khi Member rời Project vẫn được giữ.

## Những hướng đã thống nhất cho Kỳ 2

- Xóa Account theo hướng soft delete; giữ định danh để attribution lịch sử còn diễn giải được.
- Nếu Account đang là Owner, chuyển Owner sang Member đủ điều kiện có thời điểm tham gia Project sớm nhất.
- Mỗi Project luôn có đúng một Owner; chặn xóa Account nếu một Project owned không có Member đủ điều kiện kế nhiệm.
- Project mà Account chỉ tham gia với vai trò Member được giữ lại; Membership bị thu hồi và Card assignment được unassign theo quy trình sẽ thiết kế ở Kỳ 2.
- Không xóa lịch sử thao tác/comment của các Project còn tồn tại chỉ vì Account bị soft-delete.
- Hard delete và cascade xóa vật lý Project/Board/Card/Comment/Notification chưa được chốt.

## Chưa phải contract triển khai

Endpoint quản trị, trạng thái thao tác, operationId/statusUrl, xử lý Invitation PENDING, retention, đồng bộ Authentication snapshot, event/outbox/inbox flow và cách giữ profile hiển thị lịch sử cần được thiết kế lại khi mở Kỳ 2. Không tái sử dụng nguyên xi flow cascade/hard-delete trong các tài liệu/plans cũ.

Tham khảo nguồn chuẩn:
- [Architecture decisions](architecture-decisions.md)
- [Kanban API contract](../api/kanban-api.md)
- Tài liệu DB/API riêng của [user-service](../../services/user-service/docs/database.md), [board-service](../../services/board-service/docs/database.md), [task-service](../../services/task-service/docs/database.md) và [notification-service](../../services/notification-service/docs/database.md)
