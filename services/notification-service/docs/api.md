# notification-service API

**Sở hữu:** Notification trong notification_db. Mỗi Notification thuộc một recipient Account; projectId, boardId, cardId là context tùy chọn, không quyết định quyền sở hữu. Contract chung: [Kanban API](../../../docs/api/kanban-api.md).

Hướng dẫn Shared: [Shared README](../../../shared/README.md).

## Route được khai báo trong source

| Method | Route | Header/query/body | Response |
|---|---|---|---|
| GET | /api/v1/notifications/me | X-User-Id; unreadOnly? mặc định false | 200 NotificationResponse[] |
| PATCH | /api/v1/notifications/{id}/read | X-User-Id; body rỗng | 200 NotificationResponse |
| POST | /internal/v1/notifications | userId, projectId?, type, title, cardId?, boardId? | 201 NotificationResponse |

Client chỉ được list/mark-read Notification của Account hiện tại. POST internal route bị tắt mặc định bằng `INTERNAL_NOTIFICATIONS_ENABLED=false`; nếu bật thì hiện vẫn không kiểm tra service token, nên chỉ bật sau khi có bảo vệ service-to-service và không expose qua Gateway. TaskEventListener còn là placeholder, nên chưa có producer event tạo Notification end-to-end. Các inbound ports cũng chưa được nối hoàn chỉnh.

Response có id, type, title, projectId, cardId, boardId, read, createdAt. Client list hiện trả mảng không phân trang. Các error mapping cụ thể xem exception handler; không coi API contract draft là runtime behavior.

## Context và phạm vi

Notification hệ thống có thể không có Project/Board/Card context. Một row có tối đa một projectId và một Board/Card context; Account inbox vẫn tổng hợp Notification đến từ nhiều Project. Kỳ 1 không xóa Notification theo Project/Board. Retention và xử lý khi Account bị soft-delete thuộc Kỳ 2.

Account deletion/Project cascade handler và job không tồn tại trong runtime Kỳ 1. Xem [architecture decisions](../../../docs/project/architecture-decisions.md) và [Kỳ 2 account-deletion note](../../../docs/project/account-deletion-skeleton-guide.md).
