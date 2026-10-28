# board_db — database do board-service sở hữu

**Database local:** `board_db` (PostgreSQL). Entity dưới đây phản ánh mapping JPA đang có, chưa phải schema Project-first hoàn chỉnh.

## Runtime hiện có

### `boards`

| Cột | Kiểu/mapping | Constraint |
|---|---|---|
| `id` | BIGINT | PK, `IDENTITY` |
| `name` | varchar(255) | not null |
| `board_key` | varchar(20) | not null, unique toàn bảng |
| `owner_id` | UUID | not null; tham chiếu logic tới Account, không FK chéo DB |

Board hiện được sở hữu trực tiếp bởi `owner_id`. Không có `project_id`. Key validation hiện cũ theo chữ hoa/số; đó không phải quy tắc key mục tiêu.

### `board_columns`

| Cột | Kiểu/mapping | Constraint |
|---|---|---|
| `id` | BIGINT | PK, `IDENTITY` |
| `board_id` | BIGINT | not null; quan hệ JPA tới `boards` |
| `name` | varchar(100) | not null |
| `position` | integer | not null |

`BoardEntity.columns` có `cascade=ALL` và `orphanRemoval=true`, được đọc theo `position ASC`. Ba Column mặc định được tạo trong domain khi request không truyền danh sách rỗng; không phải dữ liệu mặc định trong DB.

## Scaffold operation cascade

### `project_deletion_operations`

| Cột | Kiểu/mapping | Constraint |
|---|---|---|
| `project_deletion_id` | UUID | PK |
| `operation_id` | UUID | not null; operation Account cha |
| `project_id` | BIGINT | not null |
| `board_ids_json` | TEXT | not null |
| `task_data_deleted`, `notifications_deleted` | boolean | not null |
| `status` | varchar(30) | not null; bắt đầu `PENDING` |
| `created_at`, `updated_at` | timestamptz | not null |

Entity có index trên `operation_id` và `project_id`. Hiện bảng operation có entity/repository nhưng chưa có Project graph hay coordinator adapter để chạy cascade.

## Thiết kế Project-first chưa có trong schema hiện tại

Các bảng Project, Membership và Invitation; quan hệ Board→Project; key chữ thường unique theo Project; trạng thái Project/Board và các constraint nghiệp vụ chưa có entity/migration trong service. Chi tiết field/ERD chưa được chốt ở tài liệu chung, nên không tự thêm schema giả định ở đây.

## Messaging và ranh giới dữ liệu

Outbox/Inbox dùng mapping từ `shared/messaging-support` nhưng phải được lưu trong `board_db`. Card/Comment thuộc `task_db`; Notification thuộc `notification_db`. Không tạo JPA foreign key chéo database. Xem [API](api.md), [quyết định chung](../../../docs/project/architecture-decisions.md) và [hướng dẫn skeleton](../../../docs/project/account-deletion-skeleton-guide.md).
