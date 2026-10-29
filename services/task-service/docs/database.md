# task_db — database do task-service sở hữu

**Database local:** task_db (PostgreSQL). task-service sở hữu Card, assignment và Comment. Project, Board, Column, Account và Notification thuộc service/database khác; các ID tham chiếu chéo là scalar, không có cross-database foreign key. Schema thay đổi cần migration có version; cấu hình ddl-auto=update hiện tại không thay migration.

Đọc [hướng dẫn Shared](../../../shared/README.md) trước khi nối event contracts hoặc Outbox/Inbox.

## Mapping đang có trong JPA source

### cards

| Cột | Mapping | Ghi chú |
|---|---|---|
| id | BIGINT, identity, PK | ID Card |
| project_id | BIGINT, not null | Project scope dùng cho API, query và cleanup; scalar reference |
| board_id, column_id | BIGINT, not null | Scalar reference tới board_db |
| title | varchar(500), not null | |
| description | TEXT, nullable | |
| status | varchar(30), nullable trong entity; mặc định builder TODO | |
| position | integer, not null; mặc định 0 | |
| due_date | timestamptz, nullable | |

### task_assignees

| Cột | Mapping | Ghi chú |
|---|---|---|
| task_id, user_id | composite PK | task_id FK nội bộ tới cards.id; user_id là scalar Account reference |
| assigned_by_user_id | UUID, not null | Actor gán |
| assigned_at | timestamptz, not null | |
| membership_version | BIGINT, not null | Generation lấy từ Project authorization snapshot |
| is_primary | boolean, not null | Kỳ 1 vẫn hỗ trợ tối đa một assignee qua API |

### membership_assignment_fences

Composite PK (project_id, user_id, membership_version); lưu event_id và fenced_at. Fence nằm cùng task_db, không FK sang board_db. Assignment write và fence check được serialize theo Project/User để một cleanup cũ không xóa assignment của lần tham gia mới.

### member_assignment_cleanup_jobs

Job bền vững riêng cho unassign nền: job_id, unique deduplication_key, unique event_id, operation_id, project_id, user_id, membership_version, source_aggregate_version, board_ids_json, status, cursor last_processed_card_id, attempt_count, last_error, created_at, updated_at, next_attempt_at. `membership_version` giữ generation để cleanup chỉ query đúng Project/User/version và các Board trong event; `source_aggregate_version` giữ `aggregateVersion` của request envelope để completion không nhầm nó với generation. Retry giữ nguyên cả hai.

### card_comments

id BIGINT PK, card_id BIGINT not null FK nội bộ tới cards.id, author_id UUID scalar, content TEXT not null, created_at timestamptz. Không xóa Comment/attribution khi Member rời Project.

## Redis authorization và messaging

Task đọc Account Authentication snapshot do user-service sở hữu và Project Membership snapshot do board-service sở hữu. Membership snapshot mang cả `membershipVersion` (generation cho assignment) và `authorizationVersion` (`aggregateVersion` dùng cho thứ tự/CAS). Redis không phải nguồn chuẩn DB. Nếu snapshot thiếu/không hợp lệ hoặc Redis lỗi, API từ chối thao tác có quyền (fail closed). Snapshot-sync jobs thuộc từng source DB; Task không sở hữu snapshot.

outbox_events và inbox_events từ shared/messaging-support được lưu cục bộ trong task_db. Outbox/Inbox là messaging dùng chung; không có Account/Project deletion job hay Board write-lock table trong schema Kỳ 1.

## Phạm vi target và giới hạn

cards.project_id, task_assignees, membership fence và cleanup job hiện được biểu diễn trong JPA entities nhưng chưa có migration version. Inbound Card use case cũng chưa được nối hoàn chỉnh; việc có entity không chứng minh schema đã được deploy. Board/Column-to-Project validation vẫn là integration TODO vì internal REST chưa được bảo mật/triển khai.

Kỳ 1 không có cards.assignee_id, task_deletion_jobs, board_write_locks, Project cascade hay Account deletion operation. Mọi tài liệu cũ mô tả các đối tượng này là lịch sử/Kỳ 2, không phải schema hiện hành.
