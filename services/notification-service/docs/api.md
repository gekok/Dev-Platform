# notification-service API — runtime hiện có và mục tiêu cascade

**Vai trò:** sở hữu Notification trong `notification_db`. Quyết định mục tiêu: [architecture decisions](../../../docs/project/architecture-decisions.md); route client mục tiêu: [Kanban API](../../../docs/api/kanban-api.md).

## API client hiện có

`NotificationController` khai báo:

| Method | Route hiện tại | Hành vi |
|---|---|---|
| GET | `/api/v1/notifications/me` | Danh sách Notification của user, có `unreadOnly` |
| PATCH | `/api/v1/notifications/{id}/read` | Đánh dấu đã đọc |

Identity hiện lấy từ `X-User-Id`; chưa có JWT parsing/authorization hoàn chỉnh trong controller.

## API nội bộ hiện có

`InternalNotificationController` khai báo `POST /internal/v1/notifications` để tạo notification. Route không thuộc Gateway. Controller không có cơ chế kiểm tra service token; comment mô tả token không phải bằng chứng endpoint đã được bảo vệ. Chưa đưa API này thành hợp đồng giao tiếp service an toàn.

`TaskEventListener` hiện là placeholder, chưa nhận event và chưa gọi `CreateNotificationPort`. Không khẳng định luồng gán/mention/move đã tạo Notification end-to-end.

## Mục tiêu và điểm còn mở

- Client chỉ list/mark-read Notification của chính Account.
- Cascade Project sẽ yêu cầu notification-service xóa dữ liệu phụ thuộc trong phạm vi Project sau khi phạm vi dữ liệu được chốt.
- Skeleton hiện truy vấn notification theo `boardIds`; notification không gắn Board và cách xử lý toàn bộ notification theo Account còn mở.
- Chưa có queue listener, Inbox/job transaction, batch worker hoặc completion publisher.
- `PR_REVIEWED` thuộc mô tả Kỳ 2 cũ; chưa phải contract Kỳ 2 đã thống nhất.

Xem [skeleton cascade](../../../docs/project/account-deletion-skeleton-guide.md) và mục còn mở trong architecture decisions.
