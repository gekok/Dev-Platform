# notification-service API

**Sở hữu:** Notification trong notification_db. Mỗi Notification thuộc một recipient Account; projectId, boardId, cardId là context tùy chọn, không quyết định quyền sở hữu. Contract chung: [Kanban API](../../../docs/api/kanban-api.md).

Hướng dẫn Shared: [Shared README](../../../shared/README.md).

## Route được khai báo trong source

| Method | Route | Header/query/body | Response |
|---|---|---|---|
| GET | /api/v1/notifications/me | X-User-Id; unreadOnly? mặc định false | 200 NotificationResponse[] |
| PATCH | /api/v1/notifications/{id}/read | X-User-Id; body rỗng | 200 NotificationResponse |
| POST | /internal/v1/notifications | userId, projectId?, type, title, cardId?, boardId? | 201 NotificationResponse |

Client chỉ được list/mark-read Notification của Account hiện tại. POST internal route bị tắt mặc định bằng `INTERNAL_NOTIFICATIONS_ENABLED=false`; nếu bật thì vẫn chưa kiểm tra service token, nên không expose qua Gateway. `TaskEventListener` đã consume `card.assigned` và `comment.mentioned` qua RabbitMQ, sau đó gọi application processor để ghi Inbox cùng Notification trong một transaction. Task-service đã có producer Outbox cho hai Notification intent này; nghiệm thu broker end-to-end vẫn cần thực hiện riêng. List/mark-read đã có application service. Controller đọc `X-User-Id`; Gateway xác thực và ghi đè header cho request đi qua Gateway, nhưng direct-service ingress vẫn còn mở.

Response có id, type, title, projectId, cardId, boardId, read, createdAt. Client list hiện trả mảng không phân trang. Các error mapping cụ thể xem exception handler; không coi API contract draft là runtime behavior.

## RabbitMQ consumer trong source

Consumer được đăng ký khi đồng thời có `MESSAGING_ENABLED=true` và `MESSAGING_CONSUMERS_ENABLED=true`.

- Exchange: `dev-platform.domain-events`.
- Queue: `notification-service.task-events`.
- Routing key: `card.assigned`, `comment.mentioned`.
- DLQ: `notification-service.task-events.dlq` qua exchange `dev-platform.dead-letter`.
- Contract: `schemaVersion=1`, `aggregateType=TASK_NOTIFICATION_INTENT`, `aggregateId=eventId`, `aggregateVersion=1`.
- `card.assigned` tạo một Notification loại `ASSIGNED`.
- `comment.mentioned` tạo một Notification loại `MENTIONED` cho mỗi recipient.
- Inbox và các Notification được ghi cùng transaction; event trùng không tạo side effect lần hai.
- Listener dùng AUTO ACK sau khi xử lý thành công. Lỗi được retry tối đa 3 lần, cách nhau 5 giây, sau đó chuyển DLQ.
- Event type không hỗ trợ được bỏ qua mà không ghi Inbox hoặc Notification.

Unit test của notification-service đã kiểm tra mapper, nhiều recipient, duplicate, rollback propagation và listener. Kiểm thử local qua RabbitMQ đã xác nhận `card.assigned`, `comment.mentioned`, duplicate và retry/DLQ; event lỗi không để lại Inbox/Notification. Luồng Task Outbox Relay → RabbitMQ và tình huống mất ACK/redelivery vẫn cần bằng chứng riêng trước khi đóng issue.

## Context và phạm vi

Notification hệ thống có thể không có Project/Board/Card context. Một row có tối đa một projectId và một Board/Card context; Account inbox vẫn tổng hợp Notification đến từ nhiều Project. Kỳ 1 không xóa Notification theo Project/Board. Retention và xử lý khi Account bị soft-delete thuộc Kỳ 2.

Account deletion/Project cascade handler và job không tồn tại trong runtime Kỳ 1. Xem [architecture decisions](../../../docs/project/architecture-decisions.md) và [Kỳ 2 account-deletion note](../../../docs/project/account-deletion-skeleton-guide.md).
