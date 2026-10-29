# notification_db — database do notification-service sở hữu

**Database local:** notification_db (PostgreSQL). notification-service sở hữu Notification theo Account nhận. user_id, project_id, board_id, card_id là ID tham chiếu logic, không FK chéo database. Cần migration có version trước khi coi entity mapping là schema đã triển khai.

Đọc [Shared README](../../../shared/README.md) để hiểu event contract và mapping Outbox/Inbox.

## Bảng notifications

| Cột | Mapping | Ghi chú |
|---|---|---|
| id | BIGINT identity, PK | |
| user_id | UUID, not null | Recipient Account; không FK sang user_db |
| project_id | BIGINT, nullable | Context Project; không quyết định recipient |
| type | varchar(30), not null; builder mặc định ASSIGNED | Type hiện có trong API/domain cần đối chiếu event contract |
| title | varchar(500), not null | |
| card_id, board_id | BIGINT, nullable | Scalar context; không FK chéo DB |
| read | boolean, not null; mặc định false | |
| created_at | timestamptz | Mapping hiện cho phép null |

JPA khai báo index (user_id, read). Query list theo recipient và thời gian cần index phù hợp sau khi có query/schema migration; chưa coi index đề xuất là benchmarked.

## Outbox/Inbox và ownership

outbox_events/inbox_events từ shared/messaging-support nằm trong notification_db riêng. Shared là thư viện, không phải database dùng chung. Notification service không có Account/Project deletion jobs hoặc deletion fences trong Kỳ 1.

Notification history không cascade theo Project/Board. Account deletion và retention policy được thiết kế ở Kỳ 2. Không thêm cross-database FK hoặc bảng Project membership vào notification_db.
