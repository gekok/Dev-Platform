# user_db — database do user-service sở hữu

**Database local:** `user_db` (PostgreSQL). Các entity bên dưới là mapping JPA hiện tại; migration/schema triển khai cần được chốt riêng, không suy ra từ việc ứng dụng có thể tự cập nhật schema.

## Dữ liệu Account hiện có

### `users`

| Cột | Kiểu/mapping | Constraint |
|---|---|---|
| `id` | UUID | Primary key; tạo bằng `GenerationType.UUID` |
| `email` | varchar(255) | not null, unique |
| `password_hash` | varchar | not null |
| `full_name` | varchar(255) | nullable |
| `status` | varchar(20) | not null; enum `ACTIVE` hoặc `DELETING`; mặc định trong builder là `ACTIVE` |

`password_hash` không được trả trong response API. Các service khác chỉ giữ User UUID làm tham chiếu, không tạo foreign key xuyên database.

## Dữ liệu operation xóa Account trong skeleton

### `account_deletion_operations`

| Cột | Kiểu/mapping | Constraint |
|---|---|---|
| `operation_id` | UUID | Primary key |
| `account_id` | UUID | not null |
| `status` | varchar(20) | not null; bắt đầu `PENDING` |
| `created_at`, `updated_at` | timestamptz | not null |

Entity khai báo index `(account_id, created_at)`. Operation hiện mới được tạo cùng trạng thái Account và Outbox; API truy vấn tiến độ chưa được expose.

### `deleted_account_tombstones`

| Cột | Kiểu/mapping | Constraint |
|---|---|---|
| `account_id` | UUID | Primary key |
| `deleted_at` | timestamptz | not null |

Tombstone phục vụ giữ dấu vết Account đã xóa để các attribution lịch sử có thể hiển thị đúng. Completion flow chưa được nối end-to-end.

## Bảng messaging dùng chung

Mỗi service lưu Outbox/Inbox trong database do chính service sở hữu; module dùng chung định nghĩa mapping, không tạo database dùng chung.

- `outbox_events`: `event_id` (PK), `event_type`, `aggregate_type`, `aggregate_id`, `aggregate_version`, `payload`, `status`, `attempt_count`, `created_at`, `next_attempt_at`, `sent_at`, `last_error`. Có index cho trạng thái/thời điểm retry.
- `inbox_events`: composite key `(consumer_name, event_id)`, kèm `event_type` và `received_at`.
- `OutboxEventWriter` yêu cầu transaction đang tồn tại; `InboxEventRecorder` cũng yêu cầu transaction đang tồn tại.
- Outbox Relay hiện dùng confirm/return và retry theo cấu hình; relay/listener không mặc định bật và chưa phải policy retry/DLQ đã chốt.

## Sở hữu dữ liệu

user-service sở hữu Account, credential, status, account deletion operation và tombstone. Membership, Invitation, Project, Board, Card, Comment và Notification thuộc database/service khác. Xem [API/status](api.md) và [quyết định chung](../../../docs/project/architecture-decisions.md).
