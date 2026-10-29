# board_db — database do board-service sở hữu

**Database local:** board_db (PostgreSQL). Theo kiến trúc Kỳ 1, board-service sẽ sở hữu Project cùng Membership, Invitation, Board và Column; project-service không phải deployable riêng. Không có foreign key sang database của user/task/notification service. Entity mapping hiện tại chưa phải migration đã triển khai.

Đọc [Shared README](../../../shared/README.md) để biết event envelope và mapping Outbox/Inbox.

## Runtime mapping hiện có trong JPA

### boards

| Cột | Mapping hiện tại |
|---|---|
| id | BIGINT identity, PK |
| name | varchar(255), not null |
| board_key | varchar(20), not null, unique toàn bảng trong mapping hiện tại |
| owner_id | UUID, not null; scalar Account reference |
| columns | quan hệ JPA nội bộ với board_columns |

BoardEntity hiện chưa có project_id; owner_id trực tiếp là mapping cũ Board-first. Không dùng nó làm schema đích Project-first.

### board_columns

| Cột | Mapping hiện tại |
|---|---|
| id | BIGINT identity, PK |
| board_id | FK nội bộ tới `boards.id`, not null |
| name | varchar(100), not null |
| position | integer, not null; thứ tự hiện được đọc tăng dần |

Board–Column là quan hệ nội bộ `board_db`; chưa có migration version.

## Schema logic mục tiêu Kỳ 1

Các bảng dưới đây mô tả thiết kế Project-first đã thống nhất, **chưa phải JPA mapping hoặc migration hiện có**. Tên bảng/cột dùng snake_case. Project/Board/Column dùng ID số; User dùng UUID scalar. Kích thước chuỗi và một số timestamp cần được phản ánh nhất quán vào API validation trước khi tạo migration.

### `projects`

| Cột | Kiểu/constraint mục tiêu | Ý nghĩa |
|---|---|---|
| `id` | BIGINT identity, PK | Project ID |
| `name` | varchar, not null | Tên Project |
| `status` | varchar(20), not null, default `ACTIVE`; CHECK `ACTIVE`, `COMPLETED`, `CANCELLED` | Vòng đời Project |
| `created_by_user_id` | UUID, not null | Scalar reference đến Account tạo Project; không phải FK chéo DB |
| `created_at`, `updated_at` | timestamptz, not null | Thời điểm UTC |

Owner hiện tại được xác định từ đúng một Membership có role `OWNER`; không lưu thêm `owner_id` làm nguồn thứ hai trong `projects`. Nếu API trả `ownerId`, giá trị đó được suy ra từ Membership. Project creation và Owner Membership phải commit cùng transaction. Mỗi Project phải có một Owner; partial unique index ngăn có hơn một Owner đang hoạt động, còn việc bảo đảm có ít nhất một Owner thuộc transaction/business rule.

### `project_memberships`

| Cột | Kiểu/constraint mục tiêu | Ý nghĩa |
|---|---|---|
| `project_id` | BIGINT, PK thành phần, FK tới `projects.id` | Project sở hữu Membership |
| `user_id` | UUID, PK thành phần | Account; scalar reference, không FK sang `user_db` |
| `role` | varchar(20), not null; CHECK `OWNER`, `MEMBER` | Vai trò trong Project |
| `status` | varchar(20), not null; CHECK `ACTIVE`, `REMOVED` | Chỉ `ACTIVE` được cấp quyền |
| `joined_at` | timestamptz, not null | Thời điểm tham gia lần hiện hành |
| `removed_at` | timestamptz, nullable | Thời điểm gỡ/rời; null khi đang active |
| `updated_at` | timestamptz, not null | Lần cập nhật gần nhất |

PK `(project_id,user_id)` ngăn Membership đang lưu bị trùng. Rejoin chuyển Membership về `ACTIVE` sau Accept và phải lấy generation mới từ `project_membership_versions`. Tạo Membership, Outbox event, version và snapshot-sync job của thao tác Accept/gỡ phải được đồng bộ theo transaction đã nêu trong architecture decisions. Unique partial index trên `(project_id)` với điều kiện `role='OWNER' AND status='ACTIVE'` ngăn hai Owner đồng thời. Index `(user_id,status,project_id)` phục vụ liệt kê Project theo Account.

### `invitations`

| Cột | Kiểu/constraint mục tiêu | Ý nghĩa |
|---|---|---|
| `id` | BIGINT identity, PK | Invitation ID dùng trong route `{inviteId}` |
| `project_id` | BIGINT, not null, FK tới `projects.id` | Project đích |
| `user_id` | UUID, not null | Người nhận; chỉ mời Account đã có, không FK sang `user_db` |
| `invited_by_user_id` | UUID, not null | Owner tạo lời mời; scalar reference |
| `status` | varchar(20), not null; CHECK `PENDING`, `ACCEPTED`, `REJECTED`, `CANCELLED` | Trạng thái lời mời |
| `created_at`, `updated_at` | timestamptz, not null | Thời gian tạo/cập nhật |
| `responded_at` | timestamptz, nullable | Thời điểm Accept/Reject |

Unique partial index trên `(project_id,user_id)` với điều kiện `status='PENDING'` ngăn lời mời chờ trùng. Index `(user_id,status,created_at)` phục vụ inbox và `(project_id,status,created_at)` phục vụ Owner xem lời mời của Project. Accept đổi Invitation sang `ACCEPTED` và tạo/reactivate Membership trong cùng transaction. Chưa thêm `expires_at`/`EXPIRED` vì chính sách hết hạn chưa chốt; mời email chưa đăng ký thuộc Kỳ 2.

### `boards` và `board_columns` mục tiêu

| Bảng/cột | Kiểu/constraint mục tiêu |
|---|---|
| `boards.id` | BIGINT identity, PK |
| `boards.project_id` | BIGINT, not null, FK nội bộ tới `projects.id` |
| `boards.name` | varchar(255), not null |
| `boards.board_key` | varchar(20), not null; chữ thường theo input contract |
| `boards.status` | varchar(20), not null, default `ACTIVE`; CHECK `ACTIVE`, `COMPLETED`, `CANCELLED` |
| `boards.created_by_user_id` | UUID, not null; scalar Account reference |
| `boards.created_at`, `boards.updated_at` | timestamptz, not null |
| `UNIQUE (project_id, board_key)` | Board key chỉ unique trong Project |
| `board_columns.id` | BIGINT identity, PK |
| `board_columns.board_id` | BIGINT, not null, FK tới `boards.id` |
| `board_columns.name` | varchar(100), not null |
| `board_columns.position` | integer, not null, CHECK `position >= 0` |
| `UNIQUE (board_id, position)` | Không trùng vị trí Column trong Board |

Target bỏ `boards.owner_id`: quyền Board kế thừa Project Membership. Board key phải được validate chữ thường ở API; việc tự chuẩn hóa chữ hoa chưa được chọn. Project/Board/Column cùng `board_db` nên có FK nội bộ; không thêm FK tới User DB hay service DB khác.

## Mapping snapshot/membership generation đã có trong source

### project_membership_versions

Composite key (project_id, user_id), `membership_version` BIGINT, `authorization_version` BIGINT và `row_version` optimistic lock. `membership_version` giữ generation qua remove và tăng khi rejoin; `authorization_version` tăng với mọi thay đổi trạng thái phân quyền, gồm remove, role change và rejoin. `row_version` chỉ là optimistic lock. Bảng này là persistence seam, chưa có Project/Membership aggregate hoặc API mutation nối vào.

### project_authorization_snapshot_sync_jobs

job_id UUID PK; operation_id UUID unique; project_id; user_id; membership_version; authorization_version; snapshot_json; status; attempt_count; last_error; created_at; updated_at; next_attempt_at. Unique constraint trên (project_id, user_id, authorization_version), index theo status/next_attempt_at. `operation_id` liên kết lần thay đổi quyền với trạng thái sync bền vững để status endpoint tương lai tra cứu. Worker dùng `authorization_version` cho Redis compare-and-set và retry. Redis cho kết quả APPLIED, ALREADY_CURRENT hoặc SUPERSEDED; job bị version mới hơn vượt qua có trạng thái `SUPERSEDED`, không phải xác nhận snapshot cũ đã có hiệu lực. Cờ worker mặc định tắt.

Các bảng Project/Membership/Invitation và `boards.project_id` ở trên chưa có JPA mapping hay migration. `projects` không có `project_key` vì field/key này chưa xuất hiện trong contract Project-first; `Board.key` là chữ thường và unique trong phạm vi Project.

## Outbox/Inbox và Redis

outbox_events/inbox_events từ shared/messaging-support nằm trong board_db riêng. Khi nối mutation, source Membership, Outbox event và snapshot-sync job phải commit trong cùng transaction. Redis là authorization snapshot dùng chung, không phải database nguồn; Task đọc snapshot trực tiếp. Snapshot apply mới là mốc revoke đã chốt.

Shared mapping chỉ là thư viện JPA/messaging; mỗi service có bảng Outbox/Inbox riêng. Bảng snapshot job và membership version hiện chưa có migration. Các unique/check/index mục tiêu cần được tạo bằng migration khi migration tool được chọn; không dùng `ddl-auto=update` làm schema versioning.

## Phạm vi Kỳ 1

Kỳ 1 không có project_deletion_operations, Account deletion operation, Project cascade coordinator/job, DELETING status hay write lock cascade. Account deletion và Owner succession được ghi định hướng ở [Kỳ 2](../../../docs/project/account-deletion-skeleton-guide.md).
