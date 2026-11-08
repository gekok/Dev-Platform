# 04 — notification-service: Notification theo Account nhận

**Sở hữu mục tiêu:** Notification thuộc Account nhận trong **notification_db**; mỗi row có thể có tối đa một Project context và một Board/Card context.

**Hợp đồng:** [Kanban API](../api/kanban-api.md), [quyết định kiến trúc](architecture-decisions.md), [API/database riêng](../../services/notification-service/docs/api.md), [ghi chú Account deletion Kỳ 2](account-deletion-skeleton-guide.md).

## Runtime đã đối chiếu

- **NotificationController** khai báo GET list và PATCH mark-read theo **X-User-Id**.
- **InternalNotificationController** là skeleton cho **POST /internal/v1/notifications**, bị tắt mặc định bằng `INTERNAL_NOTIFICATIONS_ENABLED=false`; nếu bật thì chưa có authentication enforcement. Không coi route này là giao tiếp service-to-service an toàn.
- **TaskEventListener** consume `card.assigned` và `comment.mentioned` từ queue `notification-service.task-events`; **NotificationRabbitConfiguration** khai báo queue/binding/DLQ. Listener dùng shared retry factory: tối đa 3 lần retry cách 5 giây sau lần đầu; lỗi contract poison đi thẳng DLQ. Consumer mặc định tắt theo feature flags.
- **ProcessTaskNotificationService** kiểm tra contract `TASK_NOTIFICATION_INTENT`, ghi Inbox cùng Notification trong một transaction, xử lý duplicate và tạo một Notification cho mỗi recipient. Không consume `MOVED` trong Kỳ 1.
- Task-service đã có producer Outbox #25 cho hai Notification intent. Kiểm thử local bằng cách publish trực tiếp vào RabbitMQ đã xác nhận routing, nhiều recipient, duplicate, rollback và DLQ của consumer; Task Outbox Relay → broker và ACK-loss/redelivery vẫn chưa được nghiệm thu end-to-end.
- `NotificationEntity` hiện có nullable `project_id` cùng recipient Account và Board/Card context. List/mark-read/create inbound ports đã có application service; controller đọc `X-User-Id`, được Gateway xác thực và ghi đè cho request đi qua Gateway. Direct-service ingress còn mở. Account/Project deletion jobs không thuộc Kỳ 1.

## Việc cần làm

- [ ] Nghiệm thu end-to-end list/mark-read chỉ cho Account hiện tại theo Gateway identity; không tin header do client tự gửi ngoài Gateway.
- [x] Dùng RabbitMQ + Outbox/Inbox cho producer→notification-service; internal REST không thuộc integration path khi chưa có bảo mật service-to-service.
- [x] Consume `card.assigned` và `comment.mentioned`, ánh xạ thành `ASSIGNED`/`MENTIONED`; không phát hoặc consume `MOVED` trong Kỳ 1.
- [x] Thêm nullable `project_id` vào NotificationEntity/DTO; giữ recipient `user_id` và nullable Board/Card context, tất cả là scalar reference không FK chéo DB.
- [x] Đảm bảo inbox Notification tổng hợp theo Account và không cascade xóa theo Project/Board trong Kỳ 1.
- [ ] Nghiệm thu producer Outbox → relay → RabbitMQ → consumer Inbox/Notification, gồm duplicate, rollback, ACK/redelivery và DLQ trên hạ tầng local.
- [x] Gỡ Account/Project deletion handler/job khỏi runtime; policy retention và deletion thuộc Kỳ 2.
- [x] Đồng bộ API/database docs riêng theo event contract #25 và consumer #26.

## Tiêu chí nghiệm thu khi triển khai

- Client chỉ đọc/đánh dấu Notification của chính mình.
- Internal route không được xem là an toàn trước khi có xác thực service-to-service.
- Mỗi Notification thuộc đúng một recipient Account; Project/Board/Card chỉ giúp điều hướng context.
- Account inbox có thể tổng hợp Notification từ nhiều Project và cả thông báo hệ thống không có Project context.
- Kỳ 1 không cascade delete Notification theo Project và không có Notification deletion job.
- Event trùng không tạo Notification lần hai; một `comment.mentioned` tạo đúng một Notification cho mỗi recipient duy nhất.
- Lỗi xử lý được retry tối đa 3 lần cách nhau 5 giây rồi chuyển DLQ theo shared policy; consumer path đã có bằng chứng local trước thay đổi này. Retry/DLQ qua policy chung, Task Outbox Relay và ACK-loss/redelivery cần nghiệm thu trước khi đóng issue.
