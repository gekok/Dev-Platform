# task_db — database do task-service sở hữu

**Database local:** `kanban_task_db` (SQL Server). task-service sở hữu Card, assignment và Comment. Project, Board, Column, Account và Notification thuộc service/database khác; các ID tham chiếu chéo là scalar, không có cross-database foreign key. Repo có [bootstrap SQL cho DB local](../../../sql/local-sqlserver-bootstrap.sql), chỉ chạy trên bốn database rỗng; chưa có versioned migration. `ddl-auto=none` không tự tạo/cập nhật schema. Cần đối chiếu physical schema đang chạy với bootstrap trước khi dựa vào nó.

Đọc [hướng dẫn Shared](../../../shared/README.md) trước khi nối event contracts hoặc Outbox/Inbox.

## Mapping đang có trong JPA source

### cards

| Cột | Mapping | Ghi chú |
|---|---|---|
| id | BIGINT, identity, PK | ID Card |
| project_id | BIGINT, not null | Project scope dùng cho API, query và cleanup; scalar reference |
| board_id, column_id | BIGINT, not null | Scalar reference tới board_db |
| title | nvarchar(500), not null | |
| description | nvarchar(max), nullable | |
| status | varchar(30), nullable trong entity; mặc định builder TODO | |
| position | integer, not null; mặc định 0 | |
| due_date | datetimeoffset, nullable | |

`CardEntity` hiện không có cột/field `aggregateVersion` hoặc optimistic-lock version. Notification intent #25 dùng `aggregateType=TASK_NOTIFICATION_INTENT`, `aggregateId=eventId`, `aggregateVersion=1`; không cần version state trên Card và không thêm cột.

Tạo Card và move dùng transaction và SQL Server `sp_getapplock` với scope Project/Board để serialize các lần ghi position qua hai use case này. Mọi đường ghi position khác phải cùng tuân thủ khóa trước khi bật move; chưa xác nhận kiểm tra đồng thời với SQL Server.

### task_assignees

| Cột | Mapping | Ghi chú |
|---|---|---|
| task_id, user_id | composite PK | task_id FK nội bộ tới cards.id; user_id là scalar Account reference |
| assigned_by_user_id | UUID, not null | Actor gán |
| assigned_at | datetimeoffset, not null | |
| membership_version | BIGINT, not null | Generation lấy từ Project authorization snapshot |
| is_primary | boolean, not null | Kỳ 1 vẫn hỗ trợ tối đa một assignee qua API |

### membership_assignment_fences

Composite PK (project_id, user_id, membership_version); lưu event_id và fenced_at. Fence nằm cùng task_db, không FK sang board_db. Assignment write và fence check được serialize theo Project/User để một cleanup cũ không xóa assignment của lần tham gia mới.

Assignment use case lấy khóa Project/User trước Card, và từ chối generation đã có fence. Cleanup batch rollback khi có lỗi; lịch retry được ghi sau rollback trong transaction riêng. Listener source bind queue theo event cleanup đã duyệt và ACK sau commit; broker redelivery chưa được xác minh E2E.

### member_assignment_cleanup_jobs

Job bền vững riêng cho unassign nền: job_id, unique deduplication_key, unique event_id, operation_id, project_id, user_id, membership_version, source_aggregate_version, board_ids_json, status, cursor last_processed_card_id, attempt_count, last_error, created_at, updated_at, next_attempt_at. `membership_version` giữ generation để cleanup chỉ query đúng Project/User/version và các Board trong event; `source_aggregate_version` giữ `aggregateVersion` của request envelope để completion không nhầm nó với generation. Retry giữ nguyên cả hai.

### card_comments

id BIGINT PK, card_id BIGINT not null FK nội bộ tới cards.id, author_id UUID scalar, content nvarchar(max) not null, created_at datetimeoffset. Không xóa Comment/attribution khi Member rời Project.

Use case Comment đọc theo Card và Project của Card, ghi author từ identity context hiện tại và thời gian UTC. `mentionedUserIds` được kiểm tra bằng Project authorization snapshot; recipient IDs riêng biệt, trừ tác giả, được ghi trong payload Outbox `comment.mentioned` cùng transaction Comment. Task không có bảng lưu mention/history; nội dung Comment không được đưa vào event. Mention yêu cầu trusted Gateway identity, mặc định fail closed. Chưa có bằng chứng nghiệm thu tích hợp Gateway/Redis.

## Redis authorization và messaging

Task đọc Account Authentication snapshot do user-service sở hữu và Project Membership snapshot do board-service sở hữu. Membership snapshot mang cả `membershipVersion` (generation cho assignment) và `authorizationVersion` (`aggregateVersion` của Membership, dùng cho thứ tự/CAS). Giá trị này không phải version của Card. Redis không phải nguồn chuẩn DB. Nếu snapshot thiếu/không hợp lệ hoặc Redis lỗi, API từ chối thao tác có quyền (fail closed). Snapshot-sync jobs thuộc từng source DB; Task không sở hữu snapshot.

`shared/messaging-support` khai báo JPA mapping cho `outbox_events.aggregate_version` và `inbox_events`; [bootstrap SQL local](../../../sql/local-sqlserver-bootstrap.sql) cũng tạo hai bảng này trong từng DB service. `EventEnvelope<T>` đã có field `aggregateVersion`, được `OutboxEventWriter` ghi vào Outbox. Đây là hạ tầng generic dùng chung, không phải version field trên `cards`. Không có versioned migration; cần xác minh schema SQL Server đang chạy khớp bootstrap trước khi bật producer.

Đối chiếu schema vật lý bằng truy vấn chỉ đọc dưới đây; không chạy lại bootstrap trên DB đã có bảng:

```sql
USE kanban_task_db;

SELECT name AS table_name
FROM sys.tables
WHERE name IN (N'cards', N'outbox_events', N'inbox_events');

SELECT TABLE_NAME, COLUMN_NAME, DATA_TYPE, IS_NULLABLE
FROM INFORMATION_SCHEMA.COLUMNS
WHERE TABLE_SCHEMA = N'dbo'
  AND ((TABLE_NAME = N'outbox_events' AND COLUMN_NAME = N'aggregate_version')
    OR (TABLE_NAME = N'cards' AND COLUMN_NAME = N'aggregate_version'));
```

Kỳ vọng theo source/bootstrap: có `outbox_events.aggregate_version`; `cards.aggregate_version` không có; hai bảng Outbox/Inbox tồn tại. Chưa có kết quả truy vấn từ SQL Server đang chạy trong bằng chứng rà soát này.

Outbox/Inbox là messaging dùng chung; không có Account/Project deletion job hay Board write-lock table trong schema Kỳ 1.

Issue #24 không tạo bảng/cột: WebSocket là kênh ephemeral. Source hiện có Gateway ticket/proxy và Task Board lookup adapter, nhưng Task runtime mặc định tắt và chưa nghiệm thu với Task/Board/Redis thật. Ticket nằm trong query string nên ingress/access log phải che giá trị `ticket`. Issue #25 dùng shared Outbox cho `card.assigned` và `comment.mentioned`; Outbox completion của assignment cleanup #22 vẫn là luồng riêng. Không cần schema/migration mới theo event-intent contract, và không thêm `cards.aggregate_version`. Vì `ddl-auto=none`, phải xác minh database SQL Server hiện tại có bảng/cột Outbox theo bootstrap trước khi bật relay; việc có mapping hoặc bootstrap không chứng minh database vật lý đã khớp.

## Phạm vi target và giới hạn

cards.project_id, task_assignees, membership fence và cleanup job hiện được biểu diễn trong JPA entities và script khởi tạo local. Card/Comment use cases đã có implementation trong source; việc có entity hoặc schema không chứng minh đã chạy E2E. Board state lookup được bảo vệ bằng `X-Service-Token` cho WebSocket authorization và Card create/move; `ColumnRelationshipValidator` nối qua `BoardWriteGuard`. Nếu Board lookup không xác minh được, create/move fail-closed bằng 503.

Kỳ 1 không có cards.assignee_id, task_deletion_jobs, board_write_locks, Project cascade hay Account deletion operation. Mọi tài liệu cũ mô tả các đối tượng này là lịch sử/Kỳ 2, không phải schema hiện hành.
