/* Kanban MVP - SQL Server starter schema
   Database-per-service. Run with permission to CREATE DATABASE.
   No cross-database foreign keys: external IDs are validated through service APIs/events.
*/
USE master;


/* 1. USER SERVICE */
Create database kanban_user_db;
USE kanban_user_db;
-- Dùng cơ sở dữ liệu user_db (Đảm bảo database đã được tạo trước)
-- USE user_db;
-- GO

-- =========================================================================
-- 1. BẢNG system_roles (Danh mục Role hệ thống)
-- =========================================================================
CREATE TABLE system_roles (
                              role_code VARCHAR(50) NOT NULL, -- PK dạng chuỗi (Ví dụ: 'USER', 'SYSTEM_ADMIN')

                              CONSTRAINT PK_system_roles PRIMARY KEY (role_code)
);

-- Seed dữ liệu mẫu hệ thống bắt buộc như thiết kế yêu cầu
INSERT INTO system_roles (role_code) VALUES ('USER'), ('SYSTEM_ADMIN');


-- =========================================================================
-- 2. BẢNG users (Tài khoản / Xác thực)
-- =========================================================================
CREATE TABLE users (
                       id UNIQUEIDENTIFIER NOT NULL,            -- ID Account dùng xuyên suốt các service
                       email VARCHAR(255) NOT NULL,             -- Sẽ được lưu trữ thống nhất (ví dụ ép lowercase ở code hoặc DB)
                       password_hash VARCHAR(255) NOT NULL,     -- Tuyệt đối không trả qua API
                       full_name NVARCHAR(150) NULL,            -- Chốt cho phép NULL khi đăng ký nhanh, dùng NVARCHAR hỗ trợ có dấu
                       status VARCHAR(30) NOT NULL,             -- Trạng thái Account (Kỳ 1 không có 'DELETING')
                       auth_version BIGINT NOT NULL DEFAULT 1,  -- Tăng khi status hoặc System Role thay đổi để đồng bộ sang Redis

                       CONSTRAINT PK_users PRIMARY KEY (id),
                       CONSTRAINT UQ_users_email UNIQUE (email) -- Unique kiểm soát trùng lặp tài khoản
);


-- =========================================================================
-- 3. BẢNG user_system_roles (Bảng trung gian gán Role cho User)
-- =========================================================================
CREATE TABLE user_system_roles (
                                   user_id UNIQUEIDENTIFIER NOT NULL,
                                   role_code VARCHAR(50) NOT NULL,
                                   granted_by UNIQUEIDENTIFIER NULL,        -- Audit reference (ID của Admin thực hiện cấp quyền)

                                   CONSTRAINT PK_user_system_roles PRIMARY KEY (user_id, role_code),
                                   CONSTRAINT FK_user_system_roles_users FOREIGN KEY (user_id) REFERENCES users(id),
                                   CONSTRAINT FK_user_system_roles_roles FOREIGN KEY (role_code) REFERENCES system_roles(role_code)
);


-- =========================================================================
-- 4. BẢNG account_authorization_snapshot_sync_jobs (Đồng bộ quyền sang Redis)
-- =========================================================================
CREATE TABLE account_authorization_snapshot_sync_jobs (
                                                          job_id UNIQUEIDENTIFIER NOT NULL,
                                                          account_id UNIQUEIDENTIFIER NOT NULL,
                                                          auth_version BIGINT NOT NULL,
                                                          status VARCHAR(30) NOT NULL,
                                                          next_attempt_at DATETIMEOFFSET NOT NULL,

                                                          CONSTRAINT PK_account_auth_snapshot_sync_jobs PRIMARY KEY (job_id),
                                                          CONSTRAINT UQ_account_auth_sync_version UNIQUE (account_id, auth_version)
);

-- Index phục vụ background worker quét các job đến hạn retry
CREATE NONCLUSTERED INDEX idx_account_snapshot_sync_due
ON account_authorization_snapshot_sync_jobs (status, next_attempt_at);


-- =========================================================================
-- 5. BẢNG outbox_events (Do shared/messaging-support quản lý trong DB local)
-- =========================================================================
CREATE TABLE outbox_events (
                               event_id UNIQUEIDENTIFIER NOT NULL,
                               aggregate_type VARCHAR(100) NOT NULL,
                               aggregate_id VARCHAR(100) NOT NULL,
                               type VARCHAR(100) NOT NULL,
                               payload NVARCHAR(MAX) NOT NULL,          -- Lưu chuỗi JSON lớn của sự kiện
                               status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
                               created_at DATETIMEOFFSET NOT NULL,
                               next_attempt_at DATETIMEOFFSET NOT NULL,

                               CONSTRAINT PK_outbox_events PRIMARY KEY (event_id),
                               CONSTRAINT CHK_outbox_events_status CHECK (status IN ('PENDING', 'PROCESSED', 'FAILED'))
);

-- Index phục vụ luồng quét gửi event đi Message Broker (Kafka/RabbitMQ)
CREATE NONCLUSTERED INDEX idx_outbox_due
ON outbox_events (status, next_attempt_at, created_at);


-- =========================================================================
-- 6. BẢNG inbox_events (Khử trùng lặp sự kiện ở phía tiêu thụ)
-- =========================================================================
CREATE TABLE inbox_events
(
    consumer_name VARCHAR(255) NOT NULL, -- Tên định danh của consumer xử lý sự kiện
                              event_id UNIQUEIDENTIFIER NOT NULL,
                              status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
                              processed_at DATETIMEOFFSET NULL,

                              CONSTRAINT PK_inbox_events PRIMARY KEY (consumer_name, event_id),
                              CONSTRAINT CHK_inbox_events_status CHECK (status IN ('PENDING', 'PROCESSED', 'FAILED'))
);


/* 2. BOARD SERVICE */
-- Dùng cơ sở dữ liệu board_db (Đảm bảo database đã được tạo trước)
-- USE board_db;
-- GO

-- =========================================================================
-- 1. BẢNG projects
-- =========================================================================
Create database kanban_board_db;
USE kanban_board_db;
CREATE TABLE projects (
                          id BIGINT IDENTITY(1,1) NOT NULL,
                          name VARCHAR(255) NOT NULL, -- Định nghĩa kích thước cụ thể thay vì varchar chung chung
                          status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
                          created_by_user_id UNIQUEIDENTIFIER NOT NULL,
                          created_at DATETIMEOFFSET NOT NULL,
                          updated_at DATETIMEOFFSET NOT NULL,

                          CONSTRAINT PK_projects PRIMARY KEY (id),
                          CONSTRAINT CHK_projects_status CHECK (status IN ('ACTIVE', 'COMPLETED', 'CANCELLED'))
);

-- =========================================================================
-- 2. BẢNG project_memberships
-- =========================================================================
CREATE TABLE project_memberships (
                                     project_id BIGINT NOT NULL,
                                     user_id UNIQUEIDENTIFIER NOT NULL,
                                     role VARCHAR(20) NOT NULL,
                                     status VARCHAR(20) NOT NULL,
                                     joined_at DATETIMEOFFSET NOT NULL,
                                     removed_at DATETIMEOFFSET NULL,
                                     updated_at DATETIMEOFFSET NOT NULL,

                                     CONSTRAINT PK_project_memberships PRIMARY KEY (project_id, user_id),
                                     CONSTRAINT FK_project_memberships_projects FOREIGN KEY (project_id) REFERENCES projects(id),
                                     CONSTRAINT CHK_project_memberships_role CHECK (role IN ('OWNER', 'MEMBER')),
                                     CONSTRAINT CHK_project_memberships_status CHECK (status IN ('ACTIVE', 'REMOVED'))
);

-- Chỉ cho phép tối đa MỘT Owner ACTIVE trong mỗi Project
CREATE UNIQUE NONCLUSTERED INDEX UX_project_memberships_one_owner
ON project_memberships (project_id)
WHERE role = 'OWNER' AND status = 'ACTIVE';

-- Phục vụ liệt kê Project theo Account nhanh hơn
CREATE NONCLUSTERED INDEX IX_project_memberships_user_status_project
ON project_memberships (user_id, status, project_id);


-- =========================================================================
-- 3. BẢNG invitations
-- =========================================================================
CREATE TABLE invitations (
                             id BIGINT IDENTITY(1,1) NOT NULL,
                             project_id BIGINT NOT NULL,
                             user_id UNIQUEIDENTIFIER NOT NULL,
                             invited_by_user_id UNIQUEIDENTIFIER NOT NULL,
                             status VARCHAR(20) NOT NULL,
                             created_at DATETIMEOFFSET NOT NULL,
                             updated_at DATETIMEOFFSET NOT NULL,
                             responded_at DATETIMEOFFSET NULL,

                             CONSTRAINT PK_invitations PRIMARY KEY (id),
                             CONSTRAINT FK_invitations_projects FOREIGN KEY (project_id) REFERENCES projects(id),
                             CONSTRAINT CHK_invitations_status CHECK (status IN ('PENDING', 'ACCEPTED', 'REJECTED', 'CANCELLED'))
);

-- Ngăn lời mời chờ trùng (Chỉ cho phép duy nhất một bản ghi PENDING giữa 1 user và 1 project)
CREATE UNIQUE NONCLUSTERED INDEX UX_invitations_pending_unique
ON invitations (project_id, user_id)
WHERE status = 'PENDING';

-- Phục vụ luồng Inbox của User
CREATE NONCLUSTERED INDEX IX_invitations_user_status_created
ON invitations (user_id, status, created_at);

-- Phục vụ luồng thống kê lời mời phía Project Owner
CREATE NONCLUSTERED INDEX IX_invitations_project_status_created
ON invitations (project_id, status, created_at);


-- =========================================================================
-- 4. BẢNG boards
-- =========================================================================
CREATE TABLE boards (
                        id BIGINT IDENTITY(1,1) NOT NULL,
                        project_id BIGINT NOT NULL,
                        name VARCHAR(255) NOT NULL,
                        board_key VARCHAR(20) NOT NULL,
                        status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
                        created_by_user_id UNIQUEIDENTIFIER NOT NULL,
                        created_at DATETIMEOFFSET NOT NULL,
                        updated_at DATETIMEOFFSET NOT NULL,

                        CONSTRAINT PK_boards PRIMARY KEY (id),
                        CONSTRAINT FK_boards_projects FOREIGN KEY (project_id) REFERENCES projects(id),
                        CONSTRAINT UQ_boards_project_key UNIQUE (project_id, board_key),
                        CONSTRAINT CHK_boards_status CHECK (status IN ('ACTIVE', 'COMPLETED', 'CANCELLED'))
);


-- =========================================================================
-- 5. BẢNG board_columns
-- =========================================================================
CREATE TABLE board_columns (
                               id BIGINT IDENTITY(1,1) NOT NULL,
                               board_id BIGINT NOT NULL,
                               name VARCHAR(100) NOT NULL,
                               position INT NOT NULL,

                               CONSTRAINT PK_board_columns PRIMARY KEY (id),
                               CONSTRAINT FK_board_columns_boards FOREIGN KEY (board_id) REFERENCES boards(id),
                               CONSTRAINT UQ_board_columns_board_position UNIQUE (board_id, position),
                               CONSTRAINT CHK_board_columns_position CHECK (position >= 0)
);


-- =========================================================================
-- 6. BẢNG project_membership_versions
-- =========================================================================
CREATE TABLE project_membership_versions (
                                             project_id BIGINT NOT NULL,
                                             user_id UNIQUEIDENTIFIER NOT NULL,
                                             membership_version BIGINT NOT NULL,
                                             authorization_version BIGINT NOT NULL,
                                             row_version INT NOT NULL, -- Optimistic lock

                                             CONSTRAINT PK_project_membership_versions PRIMARY KEY (project_id, user_id)
);


-- =========================================================================
-- 7. BẢNG project_authorization_snapshot_sync_jobs
-- =========================================================================
CREATE TABLE project_authorization_snapshot_sync_jobs (
                                                          job_id UNIQUEIDENTIFIER NOT NULL,
                                                          operation_id UNIQUEIDENTIFIER NOT NULL,
                                                          project_id BIGINT NOT NULL,
                                                          user_id UNIQUEIDENTIFIER NOT NULL,
                                                          membership_version BIGINT NOT NULL,
                                                          authorization_version BIGINT NOT NULL,
                                                          snapshot_json NVARCHAR(MAX) NOT NULL, -- Định dạng JSON lưu chuỗi lớn trong MS SQL
                                                          status VARCHAR(50) NOT NULL,
                                                          attempt_count INT NOT NULL DEFAULT 0,
                                                          last_error NVARCHAR(MAX) NULL,
                                                          created_at DATETIMEOFFSET NOT NULL,
                                                          updated_at DATETIMEOFFSET NOT NULL,
                                                          next_attempt_at DATETIMEOFFSET NOT NULL,

                                                          CONSTRAINT PK_snapshot_sync_jobs PRIMARY KEY (job_id),
                                                          CONSTRAINT UQ_snapshot_sync_jobs_operation UNIQUE (operation_id),
                                                          CONSTRAINT UQ_snapshot_sync_jobs_version UNIQUE (project_id, user_id, authorization_version)
);

-- Index phục vụ Worker quét job chạy lại
CREATE NONCLUSTERED INDEX IX_snapshot_sync_jobs_status_next_attempt
ON project_authorization_snapshot_sync_jobs (status, next_attempt_at);


-- BẢNG OUTBOX / INBOX (cùng schema với user_db và task_db)
CREATE TABLE outbox_events (
                               event_id        UNIQUEIDENTIFIER NOT NULL,
                               event_type      VARCHAR(120)     NOT NULL,
                               aggregate_type  VARCHAR(80)      NOT NULL,
                               aggregate_id    VARCHAR(80)      NOT NULL,
                               aggregate_version BIGINT          NOT NULL,
                               payload         NVARCHAR(MAX)    NOT NULL,
                               status          VARCHAR(20)      NOT NULL DEFAULT 'PENDING',
                               attempt_count   INT              NOT NULL DEFAULT 0,
                               created_at      DATETIMEOFFSET   NOT NULL,
                               next_attempt_at DATETIMEOFFSET   NOT NULL,
                               sent_at         DATETIMEOFFSET   NULL,
                               last_error      NVARCHAR(MAX)    NULL,

                               CONSTRAINT PK_outbox_events PRIMARY KEY (event_id),
                               CONSTRAINT CHK_outbox_events_status CHECK (status IN ('PENDING','SENT'))
);

CREATE NONCLUSTERED INDEX idx_outbox_due
ON outbox_events (status, next_attempt_at, created_at);

CREATE TABLE inbox_events (
                              consumer_name VARCHAR(255)     NOT NULL,
                              event_id      UNIQUEIDENTIFIER NOT NULL,
                              status        VARCHAR(20)      NOT NULL DEFAULT 'PENDING',
                              processed_at  DATETIMEOFFSET   NULL,

                              CONSTRAINT PK_inbox_events PRIMARY KEY (consumer_name, event_id),
                              CONSTRAINT CHK_inbox_events_status CHECK (status IN ('PENDING','PROCESSED','FAILED'))
);


/* 4. TASK SERVICE */
create database kanban_task_db;
USE kanban_task_db;
-- Dùng cơ sở dữ liệu task_db (Đảm bảo database đã được tạo trước)
-- USE task_db;
-- GO

-- =========================================================================
-- 1. BẢNG cards
-- =========================================================================
CREATE TABLE cards (
                       id BIGINT IDENTITY(1,1) NOT NULL,
                       project_id BIGINT NOT NULL,              -- Scalar reference phục vụ query & cleanup
                       board_id BIGINT NOT NULL,               -- Scalar reference tới board_db
                       column_id BIGINT NOT NULL,              -- Scalar reference tới board_db
                       title VARCHAR(500) NOT NULL,            -- Tiêu đề thẻ task
                       description NVARCHAR(MAX) NULL,          -- Dùng NVARCHAR(MAX) thay cho TEXT cũ
                       status VARCHAR(30) NULL DEFAULT 'TODO', -- Mặc định builder TODO, cho phép null trong entity
                       position INT NOT NULL DEFAULT 0,
                       due_date DATETIMEOFFSET NULL,

                       CONSTRAINT PK_cards PRIMARY KEY (id)
);

-- Index tối ưu hóa việc lấy danh sách Card theo Column và vị trí kéo thả (Kanban Board query)
CREATE NONCLUSTERED INDEX IX_cards_column_position
ON cards (column_id, position)
INCLUDE (title, status, due_date);


-- =========================================================================
-- 2. BẢNG task_assignees
-- =========================================================================
CREATE TABLE task_assignees (
                                task_id BIGINT NOT NULL,
                                user_id UNIQUEIDENTIFIER NOT NULL,       -- Scalar Account reference
                                assigned_by_user_id UNIQUEIDENTIFIER NOT NULL,
                                assigned_at DATETIMEOFFSET NOT NULL,
                                membership_version BIGINT NOT NULL,      -- Generation phục vụ unassign fence
                                is_primary BIT NOT NULL,                 -- Kỳ 1 hỗ trợ tối đa 1 thông qua API

                                CONSTRAINT PK_task_assignees PRIMARY KEY (task_id, user_id),
                                CONSTRAINT FK_task_assignees_cards FOREIGN KEY (task_id) REFERENCES cards(id) ON DELETE CASCADE
);

-- Bảo đảm business rule Kỳ 1: Chỉ cho phép tối đa MỘT assignee là Primary trên mỗi thẻ Task
CREATE UNIQUE NONCLUSTERED INDEX UX_task_assignees_one_primary
ON task_assignees (task_id)
WHERE is_primary = 1;


-- =========================================================================
-- 3. BẢNG membership_assignment_fences
-- =========================================================================
CREATE TABLE membership_assignment_fences (
                                              project_id BIGINT NOT NULL,
                                              user_id UNIQUEIDENTIFIER NOT NULL,
                                              membership_version BIGINT NOT NULL,      -- Generation chặn các gói tin cleanup cũ tự xử lý sai vị trí
                                              event_id UNIQUEIDENTIFIER NOT NULL,
                                              fenced_at DATETIMEOFFSET NOT NULL,

                                              CONSTRAINT PK_membership_assignment_fences PRIMARY KEY (project_id, user_id, membership_version)
);


-- =========================================================================
-- 4. BẢNG member_assignment_cleanup_jobs (Luồng unassign nền)
-- =========================================================================
CREATE TABLE member_assignment_cleanup_jobs (
                                                job_id UNIQUEIDENTIFIER NOT NULL,
                                                deduplication_key VARCHAR(255) NOT NULL,
                                                event_id UNIQUEIDENTIFIER NOT NULL,
                                                operation_id UNIQUEIDENTIFIER NOT NULL,
                                                project_id BIGINT NOT NULL,
                                                user_id UNIQUEIDENTIFIER NOT NULL,
                                                membership_version BIGINT NOT NULL,      -- Generation để query đúng phạm vi phiên làm việc
                                                source_aggregate_version BIGINT NOT NULL,-- Giữ vị trí phiên cũ tránh nhầm với generation
                                                board_ids_json NVARCHAR(MAX) NOT NULL,   -- Danh sách board dạng chuỗi JSON lớn
                                                status VARCHAR(50) NOT NULL,
                                                cursor_last_processed_card_id BIGINT NULL,
                                                attempt_count INT NOT NULL DEFAULT 0,
                                                last_error NVARCHAR(MAX) NULL,
                                                created_at DATETIMEOFFSET NOT NULL,
                                                updated_at DATETIMEOFFSET NOT NULL,
                                                next_attempt_at DATETIMEOFFSET NOT NULL,

                                                CONSTRAINT PK_member_assignment_cleanup_jobs PRIMARY KEY (job_id),
                                                CONSTRAINT UQ_cleanup_jobs_deduplication_key UNIQUE (deduplication_key),
                                                CONSTRAINT UQ_cleanup_jobs_event_id UNIQUE (event_id)
);

-- Index phục vụ Worker chạy ngầm quét các Job dọn dẹp cần thực thi
CREATE NONCLUSTERED INDEX IX_cleanup_jobs_status_next_attempt
ON member_assignment_cleanup_jobs (status, next_attempt_at);


-- =========================================================================
-- 5. BẢNG card_comments
-- =========================================================================
CREATE TABLE card_comments (
                               id BIGINT IDENTITY(1,1) NOT NULL,
                               card_id BIGINT NOT NULL,
                               author_id UNIQUEIDENTIFIER NOT NULL,     -- Scalar reference (Không bị xóa khi rời Project)
                               content NVARCHAR(MAX) NOT NULL,          -- Nội dung comment dùng NVARCHAR(MAX) hỗ trợ UTF-8 đầy đủ
                               created_at DATETIMEOFFSET NOT NULL,

                               CONSTRAINT PK_card_comments PRIMARY KEY (id),
                               CONSTRAINT FK_card_comments_cards FOREIGN KEY (card_id) REFERENCES cards(id) ON DELETE CASCADE
);

-- Index tối ưu luồng lấy danh sách thảo luận (Comment timeline) của Card
CREATE NONCLUSTERED INDEX IX_card_comments_card_timeline
ON card_comments (card_id, created_at DESC);


-- =========================================================================
-- 6. BẢNG OUTBOX / INBOX (Hạ tầng messaging tích hợp trong task_db)
-- =========================================================================
CREATE TABLE outbox_events (
                               event_id UNIQUEIDENTIFIER NOT NULL,
                               aggregate_type VARCHAR(100) NOT NULL,
                               aggregate_id VARCHAR(100) NOT NULL,
                               type VARCHAR(100) NOT NULL,
                               payload NVARCHAR(MAX) NOT NULL,
                               status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
                               created_at DATETIMEOFFSET NOT NULL,
                               next_attempt_at DATETIMEOFFSET NOT NULL,

                               CONSTRAINT PK_outbox_events PRIMARY KEY (event_id),
                               CONSTRAINT CHK_outbox_events_status CHECK (status IN ('PENDING', 'PROCESSED', 'FAILED'))
);

CREATE NONCLUSTERED INDEX idx_outbox_due
ON outbox_events (status, next_attempt_at, created_at);

CREATE TABLE inbox_events (
                              consumer_name VARCHAR(255) NOT NULL,
                              event_id UNIQUEIDENTIFIER NOT NULL,
                              status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
                              processed_at DATETIMEOFFSET NULL,

                              CONSTRAINT PK_inbox_events PRIMARY KEY (consumer_name, event_id),
                              CONSTRAINT CHK_inbox_events_status CHECK (status IN ('PENDING', 'PROCESSED', 'FAILED'))
);


/* 5. NOTIFICATION SERVICE */
create database kanban_notification_db;
USE kanban_notification_db;
-- Dùng cơ sở dữ liệu notification_db (Đảm bảo database đã được tạo trước)
-- USE notification_db;
-- GO

-- =========================================================================
-- 1. BẢNG notifications
-- =========================================================================
CREATE TABLE notifications (
                               id BIGINT IDENTITY(1,1) NOT NULL,
                               user_id UNIQUEIDENTIFIER NOT NULL,        -- Recipient Account (Scalar reference)
                               project_id BIGINT NULL,                  -- Context Project (Nullable)
                               type VARCHAR(30) NOT NULL DEFAULT 'ASSIGNED', -- Phân loại thông báo
                               title NVARCHAR(500) NOT NULL,            -- Dùng NVARCHAR hỗ trợ tiếng Việt có dấu cho tiêu đề
                               board_id BIGINT NULL,                    -- Scalar context
                               card_id BIGINT NULL,                     -- Scalar context
    [read] BIT NOT NULL DEFAULT 0,           -- Mặc định false (0) trong MS SQL
                               created_at DATETIMEOFFSET NULL,          -- Mapping hiện tại cho phép null

                               CONSTRAINT PK_notifications PRIMARY KEY (id)
);

-- =========================================================================
-- 2. TỐI ƯU HÓA INDEX (CHỈ MỤC)
-- =========================================================================

-- Index cơ bản theo JPA khai báo (Hỗ trợ query đếm số tin nhắn chưa đọc/đã đọc của user)
CREATE NONCLUSTERED INDEX IX_notifications_user_read
ON notifications (user_id, [read]);

-- Index mở rộng tối ưu cho tác vụ: Liệt kê danh sách thông báo theo người nhận sắp xếp theo thời gian gần nhất
-- Bao gồm cả trường [read] và title để tối ưu hóa hiệu năng truy vấn (Covering Index)
CREATE NONCLUSTERED INDEX IX_notifications_recipient_timeline
ON notifications (user_id, created_at DESC)
INCLUDE ([read], title, type);


-- =========================================================================
-- 3. BẢNG OUTBOX/INBOX (Nằm trong notification_db riêng theo shared library)
-- =========================================================================

-- Cấu trúc mẫu cho outbox_events của riêng notification_service
CREATE TABLE outbox_events (
                               id UNIQUEIDENTIFIER NOT NULL,
                               aggregate_type VARCHAR(100) NOT NULL,
                               aggregate_id VARCHAR(100) NOT NULL,
                               type VARCHAR(100) NOT NULL,
                               payload NVARCHAR(MAX) NOT NULL,          -- Dữ liệu sự kiện dạng JSON chuỗi lớn
                               status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
                               created_at DATETIMEOFFSET NOT NULL,

                               CONSTRAINT PK_outbox_events PRIMARY KEY (id),
                               CONSTRAINT CHK_outbox_status CHECK (status IN ('PENDING', 'PROCESSED', 'FAILED'))
);

CREATE NONCLUSTERED INDEX IX_outbox_events_status
ON outbox_events (status, created_at);

-- Cấu trúc mẫu cho inbox_events để chống trùng lặp sự kiện tiêu thụ từ Kafka/RabbitMQ
CREATE TABLE inbox_events (
                              id UNIQUEIDENTIFIER NOT NULL,
                              message_handler VARCHAR(255) NOT NULL,   -- Tên hàm/class xử lý để tránh trùng chéo định danh
                              status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
                              created_at DATETIMEOFFSET NOT NULL,
                              processed_at DATETIMEOFFSET NULL,

                              CONSTRAINT PK_inbox_events PRIMARY KEY (id, message_handler),
                              CONSTRAINT CHK_inbox_status CHECK (status IN ('PENDING', 'PROCESSED', 'FAILED'))
);


/* IMPORTANT IMPLEMENTATION NOTES
  - No FK from ProjectMembers.UserId to Kanban_User_DB, or from Tasks.BoardId/ColumnId
    to Kanban_Board_DB: services own their databases. Validate references via APIs/events.
  - In Project Service, create Project + initial PROJECT_MANAGER membership in one transaction.
  - Prevent the last PROJECT_MANAGER from leaving/removal/deactivation in application logic.
  - WIP limit is a soft warning, not a blocking constraint; PM override should emit an audit/activity event.
  - Store timestamps in UTC; convert for display in client timezone.
  - For reliable RabbitMQ publishing, add a transactional Outbox table to event-producing services.
  - API Gateway validates/routes requests, but each owning service must enforce authorization.
*/
USE kanban_user_db;
GO

-- Xóa dữ liệu cũ nếu có (Tránh trùng lặp PK khi chạy lại)
TRUNCATE TABLE user_system_roles;
DELETE FROM users;
TRUNCATE TABLE account_authorization_snapshot_sync_jobs;
TRUNCATE TABLE outbox_events;
TRUNCATE TABLE inbox_events;

-- Định nghĩa sẵn 2 UUID cố định để dùng làm tham chiếu scalar sang các DB khác
-- User 1 (Admin): 'E7B3A2C1-3094-4F3B-910A-7E82845E0B2D'
-- User 2 (Member): 'A4F9C8D2-5061-4B9F-834C-9D12756F3A1E'

-- 1. Chèn tài khoản người dùng
INSERT INTO users (id, email, password_hash, full_name, status, auth_version)
VALUES
    ('E7B3A2C1-3094-4F3B-910A-7E82845E0B2D', 'admin@kanban.local', '$2a$12$L7R2vS...', N'Nguyễn Văn Admin', 'ACTIVE', 1),
    ('A4F9C8D2-5061-4B9F-834C-9D12756F3A1E', 'member@kanban.local', '$2a$12$K8M3wT...', N'Trần Văn Member', 'ACTIVE', 1);

-- 2. Gán quyền hệ thống (Giao diện bảng trung gian)
INSERT INTO user_system_roles (user_id, role_code, granted_by)
VALUES
    ('E7B3A2C1-3094-4F3B-910A-7E82845E0B2D', 'SYSTEM_ADMIN', NULL),
    ('E7B3A2C1-3094-4F3B-910A-7E82845E0B2D', 'USER', NULL),
    ('A4F9C8D2-5061-4B9F-834C-9D12756F3A1E', 'USER', 'E7B3A2C1-3094-4F3B-910A-7E82845E0B2D');

-- 3. Tạo Job đồng bộ quyền sang Redis (Trạng thái thành công)
INSERT INTO account_authorization_snapshot_sync_jobs (job_id, account_id, auth_version, status, next_attempt_at)
VALUES
    (NEWID(), 'E7B3A2C1-3094-4F3B-910A-7E82845E0B2D', 1, 'SUCCESS', SYSDATETIMEOFFSET()),
    (NEWID(), 'A4F9C8D2-5061-4B9F-834C-9D12756F3A1E', 1, 'SUCCESS', SYSDATETIMEOFFSET());

-- 4. Sự kiện mẫu trong bảng Outbox (Thông báo User đã kích hoạt thành công)
INSERT INTO outbox_events (event_id, aggregate_type, aggregate_id, type, payload, status, created_at, next_attempt_at)
VALUES
    (NEWID(), 'USER', 'A4F9C8D2-5061-4B9F-834C-9D12756F3A1E', 'USER_ACTIVATED', '{"userId":"A4F9C8D2-5061-4B9F-834C-9D12756F3A1E","email":"member@kanban.local"}', 'PROCESSED', SYSDATETIMEOFFSET(), SYSDATETIMEOFFSET());


USE kanban_board_db;
GO

-- Xóa sạch dữ liệu cũ theo đúng thứ tự ràng buộc khóa ngoại (FK)
TRUNCATE TABLE project_authorization_snapshot_sync_jobs;
TRUNCATE TABLE project_membership_versions;
TRUNCATE TABLE board_columns;
DELETE FROM boards;
TRUNCATE TABLE invitations;
DELETE FROM project_memberships;
DELETE FROM projects;

-- 1. Chèn Dự án mẫu (ID Tự tăng sẽ bắt đầu từ 1)
SET IDENTITY_INSERT projects ON;
INSERT INTO projects (id, name, status, created_by_user_id, created_at, updated_at)
VALUES (1, N'Dự án Phát triển Kanban MVP', 'ACTIVE', 'E7B3A2C1-3094-4F3B-910A-7E82845E0B2D', SYSDATETIMEOFFSET(), SYSDATETIMEOFFSET());
SET IDENTITY_INSERT projects OFF;

-- 2. Chèn Thành viên Dự án (Admin làm Owner, Member làm Thành viên thường)
INSERT INTO project_memberships (project_id, user_id, role, status, joined_at, removed_at, updated_at)
VALUES
    (1, 'E7B3A2C1-3094-4F3B-910A-7E82845E0B2D', 'OWNER', 'ACTIVE', SYSDATETIMEOFFSET(), NULL, SYSDATETIMEOFFSET()),
    (1, 'A4F9C8D2-5061-4B9F-834C-9D12756F3A1E', 'MEMBER', 'ACTIVE', SYSDATETIMEOFFSET(), NULL, SYSDATETIMEOFFSET());

-- 3. Lưu vết Lời mời tham gia Dự án lịch sử
SET IDENTITY_INSERT invitations ON;
INSERT INTO invitations (id, project_id, user_id, invited_by_user_id, status, created_at, updated_at, responded_at)
VALUES (1, 1, 'A4F9C8D2-5061-4B9F-834C-9D12756F3A1E', 'E7B3A2C1-3094-4F3B-910A-7E82845E0B2D', 'ACCEPTED', SYSDATETIMEOFFSET(), SYSDATETIMEOFFSET(), SYSDATETIMEOFFSET());
SET IDENTITY_INSERT invitations OFF;

-- 4. Chèn Bảng công việc (Board ID = 1)
SET IDENTITY_INSERT boards ON;
INSERT INTO boards (id, project_id, name, board_key, status, created_by_user_id, created_at, updated_at)
VALUES (1, 1, N'Sprint 1 - Core Features', 'sprint-1', 'ACTIVE', 'E7B3A2C1-3094-4F3B-910A-7E82845E0B2D', SYSDATETIMEOFFSET(), SYSDATETIMEOFFSET());
SET IDENTITY_INSERT boards OFF;

-- 5. Chèn các Cột trạng thái công việc (Column ID = 1, 2, 3)
SET IDENTITY_INSERT board_columns ON;
INSERT INTO board_columns (id, board_id, name, position)
VALUES
    (1, 1, N'Cần làm (Todo)', 0),
    (2, 1, N'Đang xử lý (In Progress)', 1),
    (3, 1, N'Hoàn thành (Done)', 2);
SET IDENTITY_INSERT board_columns OFF;

-- 6. Lưu Generation phiên bản phân quyền thành viên dự án
INSERT INTO project_membership_versions (project_id, user_id, membership_version, authorization_version, row_version)
VALUES
    (1, 'E7B3A2C1-3094-4F3B-910A-7E82845E0B2D', 1, 1, 0),
    (1, 'A4F9C8D2-5061-4B9F-834C-9D12756F3A1E', 1, 1, 0);


USE kanban_task_db;
GO

-- Xóa dữ liệu cũ theo đúng thứ tự ràng buộc khóa ngoại (FK) nội bộ
TRUNCATE TABLE task_assignees;
TRUNCATE TABLE membership_assignment_fences;
TRUNCATE TABLE member_assignment_cleanup_jobs;
DELETE FROM card_comments;
DELETE FROM cards;
TRUNCATE TABLE outbox_events;
TRUNCATE TABLE inbox_events;

-- 1. Chèn các Thẻ Task (Card) mẫu tham chiếu qua ID logic của bảng/cột từ board_db
SET IDENTITY_INSERT cards ON;
INSERT INTO cards (id, project_id, board_id, column_id, title, description, status, position, due_date)
VALUES
    (1, 1, 1, 1, N'Thiết kế giao diện Đăng ký/Đăng nhập', N'Yêu cầu hoàn thiện màn hình Figma trước thứ 4', 'TODO', 0, NULL),
    (2, 1, 1, 2, N'Cài đặt Database SQL Server cho cụm Microservices', N'Triển khai starter schema cho 4 database độc lập', 'ACTIVE', 0, SYSDATETIMEOFFSET());
SET IDENTITY_INSERT cards OFF;

-- 2. Gán người xử lý Task (Assignee) cho Card số 2 (Gán cho Member)
INSERT INTO task_assignees (task_id, user_id, assigned_by_user_id, assigned_at, membership_version, is_primary)
VALUES (2, 'A4F9C8D2-5061-4B9F-834C-9D12756F3A1E', 'E7B3A2C1-3094-4F3B-910A-7E82845E0B2D', SYSDATETIMEOFFSET(), 1, 1);

-- 3. Tạo hàng rào phân quyền (Fence) kiểm soát luồng chạy ngầm để đảm bảo tính an toàn dữ liệu
INSERT INTO membership_assignment_fences (project_id, user_id, membership_version, event_id, fenced_at)
VALUES (1, 'A4F9C8D2-5061-4B9F-834C-9D12756F3A1E', 1, NEWID(), SYSDATETIMEOFFSET());

-- 4. Thêm bình luận trao đổi (Comment) trong Card số 2
SET IDENTITY_INSERT card_comments ON;
INSERT INTO card_comments (id, card_id, author_id, content, created_at)
VALUES (1, 2, 'A4F9C8D2-5061-4B9F-834C-9D12756F3A1E', N'Tôi đã hoàn thành phần script tạo bảng, đang chuyển sang test các chỉ mục index.', SYSDATETIMEOFFSET());
SET IDENTITY_INSERT card_comments OFF;


USE kanban_notification_db;
GO

-- Xóa sạch dữ liệu cũ
DELETE FROM notifications;
TRUNCATE TABLE outbox_events;
TRUNCATE TABLE inbox_events;

-- 1. Chèn thông báo mẫu gửi cho người nhận (Member nhận thông báo được gán vào Card số 2)
SET IDENTITY_INSERT notifications ON;
INSERT INTO notifications (id, user_id, project_id, type, title, board_id, card_id, [read], created_at)
VALUES
    (1, 'A4F9C8D2-5061-4B9F-834C-9D12756F3A1E', 1, 'ASSIGNED', N'Bạn đã được Nguyễn Văn Admin gán vào công việc: Cài đặt Database SQL Server cho cụm Microservices', 1, 2, 0, SYSDATETIMEOFFSET());
SET IDENTITY_INSERT notifications OFF;

-- 2. Chèn bản ghi Inbox ghi nhận sự kiện hạ tầng từ Message Broker gửi sang đã được xử lý xong (Khử trùng lặp)
INSERT INTO inbox_events (id, message_handler, status, created_at, processed_at)
VALUES (NEWID(), 'TaskAssignedNotificationHandler', 'PROCESSED', SYSDATETIMEOFFSET(), SYSDATETIMEOFFSET());
