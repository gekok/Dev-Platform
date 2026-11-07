# user_db — database do user-service sở hữu

**Database local:** `kanban_user_db` (SQL Server). Tài liệu tách mapping trong skeleton hiện tại khỏi thiết kế mục tiêu; Kỳ 1 dùng script khởi tạo local cho database trống và chưa dùng migration.

**Thiết kế mục tiêu Kỳ 1:** user-service sở hữu Account/Auth, trạng thái Account, `SystemRoles`/`UserSystemRoles`, `auth_version` và Account authorization snapshot-sync job. Outbox/Inbox được lưu trong `user_db`. Account deletion/operation/tombstone được hoãn sang Kỳ 2.

**Ghi chú source:** skeleton còn `domain.User` (`@Deprecated`) có annotation mapping cũ; `UserServiceApplication` chỉ scan entity trong infrastructure và shared, nên `UserEntity` là mapping được sử dụng cho `users`.

## Mapping Account trong skeleton

### `users`

| Cột | Kiểu/mapping | Constraint |
|---|---|---|
| `id` | UUID | Primary key; tạo bằng `GenerationType.UUID` |
| `email` | varchar(255) | not null, unique |
| `password_hash` | varchar | not null |
| `full_name` | nvarchar(255) | nullable |
| `status` | varchar(20) | not null; runtime enum hiện tại được đọc từ source; Kỳ 1 không dùng `DELETING` |

`password_hash` không được trả trong response API. Các service khác chỉ giữ User UUID làm tham chiếu, không tạo foreign key xuyên database.

## System roles và authorization snapshot

`SystemRoles` định nghĩa system role; `UserSystemRoles` gán role cho Account. Đây là schema mục tiêu đã thống nhất, không phải Project role. Account mang `SYSTEM_ADMIN` chỉ quản trị Account và không được làm Owner/Member trong Project.

| Bảng | Field mục tiêu | Constraint |
|---|---|---|
| `system_roles` | `role_code`, `description` | `role_code` là PK; seed role `USER` và `SYSTEM_ADMIN` |
| `user_system_roles` | `user_id`, `role_code`, `granted_at`, `granted_by` | PK `(user_id,role_code)`; FK nội bộ tới `users` và `system_roles` |
| `users` | `auth_version` | Version tăng khi Account status hoặc SystemRoles đổi |
| `account_authorization_snapshot_sync_jobs` | `job_id`, `account_id`, `auth_version`, `snapshot_json`, retry/status/timestamps | JPA skeleton map trong `user_db`; script khởi tạo local tạo bảng rỗng |

Mỗi lần đổi Account status/role, source row, Outbox event và snapshot-sync job phải commit trong cùng transaction. user-service retry apply snapshot vào Redis theo `auth_version`; Redis apply mới là mốc có hiệu lực trong authorization path. SQL bootstrap local seed Account System Admin mẫu trong `user_db`; Kỳ 1 không dùng Flyway. API cấp/thu hồi role vẫn còn mở.

## Bảng messaging dùng chung

Mỗi service lưu Outbox/Inbox trong database do chính service sở hữu; module dùng chung định nghĩa mapping, không tạo database dùng chung.

- `outbox_events`: `event_id` (PK), `event_type`, `aggregate_type`, `aggregate_id`, `aggregate_version`, `payload`, `status`, `attempt_count`, `created_at`, `next_attempt_at`, `sent_at`, `last_error`. Có index cho trạng thái/thời điểm retry.
- `inbox_events`: composite key `(consumer_name, event_id)`, kèm `event_type` và `received_at`.
- `OutboxEventWriter` yêu cầu transaction đang tồn tại; `InboxEventRecorder` cũng yêu cầu transaction đang tồn tại.
- Outbox Relay hiện dùng confirm/return và retry theo cấu hình; relay/listener không mặc định bật. Chính sách consumer đã chốt là retry tối đa 3 lần cách nhau 5 giây rồi chuyển DLQ, nhưng chưa được nối runtime; chi tiết queue và xử lý DLQ còn mở, không áp dụng giới hạn này cho Outbox Relay.
- Bảng cột/kiểu/nullability đầy đủ của hai mapping chung nằm tại [shared README](../../../shared/README.md), mục `Physical table mappings`; cùng mapping này được tạo riêng trong `user_db`.

## Shared modules trong user-service

### Cách đọc

Đọc [hướng dẫn `shared/`](../../../shared/README.md) theo thứ tự ở đó: `event-contracts` để biết envelope/payload, `messaging-support` để biết Outbox/Inbox, rồi đối chiếu bảng vật lý và feature flags trong tài liệu này. Với quyết định nghiệp vụ hoặc event còn provisional, kiểm tra thêm [architecture decisions](../../../docs/project/architecture-decisions.md). `shared/` cung cấp code messaging; không chứa Redis Authentication/Authorization Snapshot.

### Tích hợp và trạng thái trong source

user-service khai báo `messaging-support`, import `MessagingSupportConfiguration` và scan entity/repository của shared. Vì vậy `outbox_events` và `inbox_events` được map vào `user_db`, không phải database riêng của shared.

- Snapshot recorder và Redis worker có skeleton; recorder chưa được gọi từ Account mutation use case. Khi nối lifecycle, recorder phải lưu Account/status/role Outbox event cùng durable sync job trong transaction nguồn. Worker retry Redis apply theo `auth_version` và chỉ chạy khi bật property.
- Outbox Relay dùng lại cùng event ID/version/payload khi retry. Mọi consumer tương lai ghi Inbox cùng thay đổi nghiệp vụ/job trước ACK.
- Các cờ messaging mặc định là `false`; cấu hình relay/consumer không đồng nghĩa snapshot sync đã chạy end-to-end.

## Sở hữu dữ liệu

user-service sở hữu Account, credential, status, SystemRoles/UserSystemRoles, Account authorization snapshot-sync job và Outbox/Inbox trong user_db. Membership, Invitation, Project, Board, Card, Comment và Notification thuộc service/database khác. Account deletion/tombstone thuộc Kỳ 2. Xem [API](api.md) và [quyết định chung](../../../docs/project/architecture-decisions.md).

## ERD runtime và quan hệ

```mermaid
erDiagram
    USERS ||--o{ USER_SYSTEM_ROLES : "user_id"
    SYSTEM_ROLES ||--o{ USER_SYSTEM_ROLES : "role_code"
    USERS ||--o{ ACCOUNT_AUTHORIZATION_SNAPSHOT_SYNC_JOBS : "account_id (logical)"
    OUTBOX_EVENTS {
        uuid event_id PK
    }
    INBOX_EVENTS {
        string consumer_name PK
        uuid event_id PK
    }
```

`user_id` và `role_code` là quan hệ nội bộ trong user_db; ID được các service khác tham chiếu vẫn là scalar, không có FK xuyên database. Snapshot-sync job lưu trạng thái retry; Redis không phải nguồn dữ liệu chuẩn.

## Bảng chi tiết và index

| Table | Khóa/unique | Quan hệ và index | Ghi chú mapping |
|---|---|---|---|
| `users` | `id` PK; `email` unique | Không có FK liên service | `password_hash` không trả qua API; `auth_version` tăng theo đổi status/system role |
| `system_roles` | `role_code` PK | Không có index phụ | Danh mục role hệ thống; seed `USER`, `SYSTEM_ADMIN` |
| `user_system_roles` | Composite PK `(user_id,role_code)` | FK local tới `users`/`system_roles` | `granted_by` là audit reference; local SQL bootstrap seed Account System Admin mẫu với role `SYSTEM_ADMIN` |
| `account_authorization_snapshot_sync_jobs` | `job_id` PK; unique `(account_id, auth_version)` | `idx_account_snapshot_sync_due(status, next_attempt_at)` | JPA skeleton cho Redis sync retry; script khởi tạo local tạo bảng rỗng |
| `outbox_events` | `event_id` PK | `idx_outbox_due(status, next_attempt_at, created_at)` | Do `shared/messaging-support` cung cấp và lưu ngay trong `user_db` |
| `inbox_events` | Composite PK `(consumer_name, event_id)` | Không có index phụ khai báo | Một event được khử trùng theo từng consumer |

Entity `users` không khai báo index ngoài unique email. Column kiểu `String` không chỉ rõ `length` dùng default của JPA/provider; schema local được mô tả trong script khởi tạo.

## Database local, schema và vòng đời dữ liệu

- SQL Server local dùng database `kanban_user_db`; cấu hình kết nối và credentials xem README ở root.
- `spring.jpa.hibernate.ddl-auto=none` và `show_sql=true` là cấu hình local hiện tại; `none` không tạo schema. Dùng [script khởi tạo local](../../../sql/local-sqlserver-bootstrap.sql) cho bốn database trống. Kỳ 1 chưa dùng migration.
- Account/status/system-role mutation, Outbox event và snapshot-sync job phải commit cùng transaction. Redis apply/retry là bước sau commit; snapshot cũ không được ghi đè version mới.
- Kỳ 1 không có Account deletion operation, tombstone hoặc cascade completion consumer.

## Mô hình đích và phạm vi sở hữu

Trong thiết kế Project-first, `user_db` là nguồn dữ liệu chuẩn của Account/Auth và system role. Project, Owner/Membership, Invitation, Board và Column không chuyển vào DB này; service khác giữ UUID Account dưới dạng scalar reference. Account deletion/Owner succession được thiết kế ở Kỳ 2.

## Schema mục tiêu Kỳ 1 — thiết kế để hoàn thiện

Các bảng dưới đây mô tả dữ liệu theo nghiệp vụ Kỳ 1. Entity và script khởi tạo local không đồng nghĩa các use case đã được nối đầy đủ. Những field có nhãn **đề xuất** cần được chốt trước khi triển khai.

### `users` (Account/Auth)

| Field | Kiểu mục tiêu | Null/khóa | Trạng thái và mục đích |
|---|---|---|---|
| `id` | UUID | PK, not null | Đã có trong skeleton; ID Account dùng xuyên service |
| `email` | varchar | not null, unique | Unique email đã có; chuẩn hóa hoa/thường chưa chốt |
| `password_hash` | varchar | not null | Đã có trong skeleton; tuyệt đối không trả qua API |
| `full_name` | nvarchar(255) | nullable trong mapping hiện tại | Đã có trong skeleton |
| `status` | enum/string | not null | Dùng trạng thái Account Kỳ 1; không có `DELETING` |
| `auth_version` | bigint | not null | Tăng khi status hoặc SystemRoles đổi; version trên snapshot Redis |

`system_roles` chứa danh mục role hệ thống (`USER`, `SYSTEM_ADMIN`); `user_system_roles` gán role cho Account, khóa chính `(user_id,role_code)` và FK nội bộ tới `users`/`system_roles`. Account mang `SYSTEM_ADMIN` chỉ quản trị Account, không có quyền Project và không được làm Owner/Member (theo quyết định #7). SQL bootstrap local seed Account System Admin mẫu; Kỳ 1 không dùng Flyway. Không nhận role do client tự gửi trong payload tạo Account.

Không thêm `project_id`, `project_role` hay FK sang `board_db`. Account deletion/Owner succession được hoãn sang Kỳ 2; Kỳ 1 không cascade/xóa Project khi Account thay đổi.

Chỉ System Admin được tạo Account qua `POST /api/v1/admin/accounts` (chốt theo issue #54); không mở public self-registration. Trong Kỳ 1, áp dụng phương án Admin đặt mật khẩu ban đầu, mật khẩu được giới hạn tối đa 72 byte UTF-8 và băm bằng BCrypt (strength 12) cùng quy tắc với login. Không lưu hoặc trả plaintext password/hash. Tài khoản mới tạo luôn nhận role mặc định `USER` và trạng thái `ACTIVE`. Không nhận role do client gửi trong payload tạo Account. SQL bootstrap local seed Account System Admin mẫu; Kỳ 1 không dùng Flyway. Việc tạo Account không tự cấp `SYSTEM_ADMIN`.

### Account deletion — Kỳ 2

Kỳ 1 không có `account_deletion_operations`, `deleted_account_tombstones`, Account deletion endpoint, `DELETING` status hoặc cascade consumer/job. Không thêm các bảng này vào schema Kỳ 1. Khi thiết kế Kỳ 2, cần làm rõ soft-delete, chuyển Owner cho Member đủ điều kiện tham gia sớm nhất và cách giữ attribution lịch sử.

### Messaging

`outbox_events` và `inbox_events` được tạo **trong `user_db`**, không dùng DB chung. Producer ghi Account status/role change + Outbox + snapshot-sync job trong một transaction. Kỳ 1 không có completion consumer cho Account deletion. Xem [shared mapping](../../../shared/README.md), mục `Physical table mappings`.

### Quan hệ/constraint mục tiêu

- `users.id` là nguồn chuẩn Account UUID; UUID trong các service khác là scalar reference, không có cross-database FK.
- Directory/profile API chỉ đọc Account do user-service sở hữu; không thêm Membership/Project foreign key hay role Project vào `user_db`.
- `auth_version` tăng khi Account status hoặc SystemRoles đổi; Redis chỉ có snapshot, không phải nguồn dữ liệu chuẩn.
- Account deletion operation và tombstone không thuộc schema Kỳ 1; thiết kế ở tài liệu Kỳ 2 khi phạm vi được mở lại.
- Kỳ 1 chưa dùng migration; `ddl-auto=none` không tự thay đổi schema. Script local chỉ khởi tạo database trống.
