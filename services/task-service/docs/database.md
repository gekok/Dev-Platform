# task_db — database do task-service sở hữu

**Database local:** `task_db` (PostgreSQL). Các bảng dưới đây phản ánh entity/repository JPA hiện có; chưa có schema Project-first hay migration được chốt.

## Dữ liệu Card và Comment hiện có

### `cards`

| Cột | Kiểu/mapping | Constraint |
|---|---|---|
| `id` | BIGINT | PK, `IDENTITY` |
| `board_id`, `column_id` | BIGINT | not null; ID tham chiếu logic sang board-service, không FK chéo DB |
| `title` | varchar(500) | not null |
| `description` | TEXT | nullable |
| `status` | varchar(30) | Java builder mặc định `TODO`; field mapping hiện nullable |
| `position` | integer | builder mặc định 0, not null |
| `assignee_id` | UUID | nullable; tham chiếu logic tới Account |
| `due_date` | timestamptz | nullable |

`CardEntity` hiện không khai báo `@Index`; index theo board/column/position trong tài liệu cũ chưa được định nghĩa trong entity này.

### `card_comments`

| Cột | Kiểu/mapping | Constraint |
|---|---|---|
| `id` | BIGINT | PK, `IDENTITY` |
| `card_id` | BIGINT | not null; quan hệ JPA tới Card trong cùng DB |
| `author_id` | UUID | not null; tham chiếu logic tới Account |
| `content` | TEXT | not null |
| `created_at` | timestamptz | nullable theo mapping hiện tại |

Entity Comment không khai báo cascade remove tại quan hệ Card; xóa cascade theo batch cần được triển khai tường minh.

## Scaffold xóa theo batch

### `task_deletion_jobs`

Các field mapping: `job_id` (UUID, PK), `deduplication_key` (varchar(200), unique/not null), `job_type`, `operation_id`, `project_deletion_id`, `project_id`, `board_ids_json`, `target_user_id`, `status`, `last_processed_comment_id`, `last_processed_card_id`, `attempt_count`, `last_error`, `created_at`, `updated_at`. Job bắt đầu `PENDING`; hai cursor bắt đầu từ 0.

### `board_write_locks`

`board_id` là PK; các field khác là `project_deletion_id` và `created_at`. Đây là persistence shape cho barrier xóa, chưa được gắn vào mọi mutation.

Repo có query batch tối đa 100 Card/Comment theo Board IDs và ID cursor; Card indexes chưa được thiết kế theo cursor mục tiêu. Không coi query batch hiện có là implementation của API cursor list Card.

## Messaging và ownership

Outbox/Inbox mapping từ `shared/messaging-support` lưu trong `task_db`. task-service sở hữu Card/Comment và job cleanup; Project, Membership, Column và Notification không thuộc database này. Xem [API](api.md) và [quyết định chung](../../../docs/project/architecture-decisions.md).
