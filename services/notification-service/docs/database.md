# notification_db — database do notification-service sở hữu

**Database local:** `notification_db` (PostgreSQL). Đây là mô tả mapping JPA hiện có, chưa phải hợp đồng migration.

## Bảng `notifications`

| Cột | Kiểu/mapping | Constraint |
|---|---|---|
| `id` | BIGINT | PK, `IDENTITY` |
| `user_id` | UUID | not null; tham chiếu logic tới Account, không FK chéo DB |
| `type` | varchar(30) | not null; builder mặc định `ASSIGNED` |
| `title` | varchar(500) | not null |
| `card_id`, `board_id` | BIGINT | nullable; ID tham chiếu logic, không FK chéo DB |
| `read` | boolean | not null; builder mặc định `false` |
| `created_at` | timestamptz | nullable theo mapping hiện tại |

Entity khai báo index `(user_id, read)` để hỗ trợ danh sách Notification chưa đọc. Các kiểu thực tế hiện có trong domain/API phải được đối chiếu với `NotificationType`; không coi `PR_REVIEWED` là type đã được duyệt cho Kỳ 2.

## Scaffold cascade

`notification_deletion_jobs` có `project_deletion_id` (PK), `operation_id`, `project_id`, `board_ids_json`, `status`, `last_processed_notification_id`, `attempt_count`, `last_error`, `created_at` và `updated_at`. Cursor khởi tạo 0; job khởi tạo `PENDING`.

Repository hiện có query tối đa 100 Notification theo Board IDs và ID cursor. Chưa có worker, Inbox transaction, listener hoặc completion Outbox. Scope xóa Notification chưa chốt cho dữ liệu không có Board ID.

## Messaging và ownership

Outbox/Inbox mapping dùng chung được lưu trong `notification_db`. Notification service không sở hữu User, Project, Board, Card hoặc Comment. Xem [API](api.md), [quyết định chung](../../../docs/project/architecture-decisions.md) và [skeleton guide](../../../docs/project/account-deletion-skeleton-guide.md).
