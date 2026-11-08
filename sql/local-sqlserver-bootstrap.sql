/* Local SQL Server bootstrap for the four empty Kanban databases.
   Run only against four empty local databases, using a principal with DDL rights.
   For sqlcmd, use -b -f 65001. In SSMS, stop execution if any error is reported.
   Operational tables are created empty; sample credentials are local-only.
   The login needs CREATE DATABASE permission when any database is missing.
*/
USE master;
GO
IF DB_ID(N'kanban_user_db') IS NULL CREATE DATABASE kanban_user_db;
GO
IF DB_ID(N'kanban_board_db') IS NULL CREATE DATABASE kanban_board_db;
GO
IF DB_ID(N'kanban_task_db') IS NULL CREATE DATABASE kanban_task_db;
GO
IF DB_ID(N'kanban_notification_db') IS NULL CREATE DATABASE kanban_notification_db;
GO
SET NOCOUNT ON;
SET XACT_ABORT ON;
SET ANSI_NULLS ON;
SET ANSI_PADDING ON;
SET ANSI_WARNINGS ON;
SET ARITHABORT ON;
SET CONCAT_NULL_YIELDS_NULL ON;
SET QUOTED_IDENTIFIER ON;
SET NUMERIC_ROUNDABORT OFF;
IF EXISTS (SELECT 1 FROM kanban_user_db.sys.tables)
   OR EXISTS (SELECT 1 FROM kanban_board_db.sys.tables)
   OR EXISTS (SELECT 1 FROM kanban_task_db.sys.tables)
   OR EXISTS (SELECT 1 FROM kanban_notification_db.sys.tables)
    THROW 50001, 'Bootstrap requires four empty databases; existing tables were not changed.', 1;
BEGIN TRANSACTION;
GO

USE kanban_user_db;
GO
CREATE TABLE dbo.users (
    id uniqueidentifier NOT NULL PRIMARY KEY,
    email varchar(255) NOT NULL,
    password_hash varchar(255) NOT NULL,
    full_name nvarchar(255) NULL,
    status varchar(20) NOT NULL,
    auth_version bigint NOT NULL,
    CONSTRAINT uq_users_email UNIQUE (email)
);
CREATE TABLE dbo.system_roles (
    role_code varchar(40) NOT NULL PRIMARY KEY,
    description nvarchar(200) NULL
);
CREATE TABLE dbo.user_system_roles (
    user_id uniqueidentifier NOT NULL,
    role_code varchar(40) NOT NULL,
    granted_at datetimeoffset NOT NULL,
    granted_by uniqueidentifier NULL,
    CONSTRAINT pk_user_system_roles PRIMARY KEY (user_id, role_code),
    CONSTRAINT fk_user_system_roles_user FOREIGN KEY (user_id) REFERENCES dbo.users(id),
    CONSTRAINT fk_user_system_roles_role FOREIGN KEY (role_code) REFERENCES dbo.system_roles(role_code)
);
CREATE TABLE dbo.account_authorization_snapshot_sync_jobs (
    job_id uniqueidentifier NOT NULL PRIMARY KEY,
    account_id uniqueidentifier NOT NULL,
    auth_version bigint NOT NULL,
    snapshot_json nvarchar(max) NOT NULL,
    status varchar(20) NOT NULL,
    attempt_count int NOT NULL,
    last_error nvarchar(max) NULL,
    created_at datetimeoffset NOT NULL,
    updated_at datetimeoffset NOT NULL,
    next_attempt_at datetimeoffset NOT NULL,
    CONSTRAINT uq_account_snapshot_version UNIQUE (account_id, auth_version)
);
CREATE INDEX idx_account_snapshot_sync_due ON dbo.account_authorization_snapshot_sync_jobs(status, next_attempt_at);
GO

USE kanban_board_db;
GO
CREATE TABLE dbo.projects (
    id bigint IDENTITY(1,1) NOT NULL PRIMARY KEY,
    name nvarchar(255) NOT NULL,
    status varchar(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','COMPLETED','CANCELLED')),
    created_by_user_id uniqueidentifier NOT NULL,
    created_at datetimeoffset NOT NULL,
    updated_at datetimeoffset NOT NULL
);
CREATE TABLE dbo.project_memberships (
    project_id bigint NOT NULL,
    user_id uniqueidentifier NOT NULL,
    role varchar(20) NOT NULL CHECK (role IN ('OWNER','MEMBER')),
    status varchar(20) NOT NULL CHECK (status IN ('ACTIVE','REMOVED')),
    joined_at datetimeoffset NOT NULL,
    removed_at datetimeoffset NULL,
    updated_at datetimeoffset NOT NULL,
    CONSTRAINT pk_project_memberships PRIMARY KEY (project_id, user_id),
    CONSTRAINT fk_project_memberships_project FOREIGN KEY (project_id) REFERENCES dbo.projects(id)
);
CREATE UNIQUE INDEX ux_project_memberships_one_active_owner ON dbo.project_memberships(project_id)
    WHERE role = 'OWNER' AND status = 'ACTIVE';
CREATE INDEX ix_project_memberships_user_status_project ON dbo.project_memberships(user_id, status, project_id);
CREATE TABLE dbo.invitations (
    id bigint IDENTITY(1,1) NOT NULL PRIMARY KEY,
    project_id bigint NOT NULL,
    user_id uniqueidentifier NOT NULL,
    invited_by_user_id uniqueidentifier NOT NULL,
    status varchar(20) NOT NULL CHECK (status IN ('PENDING','ACCEPTED','REJECTED','CANCELLED')),
    created_at datetimeoffset NOT NULL,
    updated_at datetimeoffset NOT NULL,
    responded_at datetimeoffset NULL,
    CONSTRAINT fk_invitations_project FOREIGN KEY (project_id) REFERENCES dbo.projects(id)
);
CREATE UNIQUE INDEX ux_invitations_pending ON dbo.invitations(project_id, user_id) WHERE status = 'PENDING';
CREATE INDEX ix_invitations_user_status_created ON dbo.invitations(user_id, status, created_at);
CREATE INDEX ix_invitations_project_status_created ON dbo.invitations(project_id, status, created_at);
CREATE TABLE dbo.boards (
    id bigint IDENTITY(1,1) NOT NULL PRIMARY KEY,
    project_id bigint NOT NULL,
    name nvarchar(255) NOT NULL,
    board_key varchar(20) NOT NULL,
    status varchar(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','COMPLETED','CANCELLED')),
    created_by_user_id uniqueidentifier NOT NULL,
    created_at datetimeoffset NOT NULL,
    updated_at datetimeoffset NOT NULL,
    CONSTRAINT fk_boards_project FOREIGN KEY (project_id) REFERENCES dbo.projects(id),
    CONSTRAINT uq_boards_project_key UNIQUE (project_id, board_key)
);
CREATE TABLE dbo.board_columns (
    id bigint IDENTITY(1,1) NOT NULL PRIMARY KEY,
    board_id bigint NOT NULL,
    name nvarchar(100) NOT NULL,
    position int NOT NULL CHECK (position >= 0),
    CONSTRAINT fk_board_columns_board FOREIGN KEY (board_id) REFERENCES dbo.boards(id),
    CONSTRAINT uq_board_columns_position UNIQUE (board_id, position)
);
CREATE TABLE dbo.project_membership_versions (
    project_id bigint NOT NULL,
    user_id uniqueidentifier NOT NULL,
    membership_version bigint NOT NULL,
    authorization_version bigint NOT NULL,
    row_version bigint NOT NULL,
    CONSTRAINT pk_project_membership_versions PRIMARY KEY (project_id, user_id)
);
CREATE TABLE dbo.project_authorization_snapshot_sync_jobs (
    job_id uniqueidentifier NOT NULL PRIMARY KEY,
    operation_id uniqueidentifier NOT NULL UNIQUE,
    project_id bigint NOT NULL,
    user_id uniqueidentifier NOT NULL,
    membership_version bigint NOT NULL,
    authorization_version bigint NOT NULL,
    snapshot_json nvarchar(max) NOT NULL,
    status varchar(20) NOT NULL,
    attempt_count int NOT NULL,
    last_error nvarchar(max) NULL,
    created_at datetimeoffset NOT NULL,
    updated_at datetimeoffset NOT NULL,
    next_attempt_at datetimeoffset NOT NULL,
    CONSTRAINT uq_project_member_snapshot_authorization_version UNIQUE (project_id, user_id, authorization_version)
);
CREATE INDEX idx_project_snapshot_sync_due ON dbo.project_authorization_snapshot_sync_jobs(status, next_attempt_at);
GO

USE kanban_task_db;
GO
CREATE TABLE dbo.cards (
    id bigint IDENTITY(1,1) NOT NULL PRIMARY KEY,
    project_id bigint NOT NULL,
    board_id bigint NOT NULL,
    column_id bigint NOT NULL,
    title nvarchar(500) NOT NULL,
    description nvarchar(max) NULL,
    status varchar(30) NULL,
    position int NOT NULL,
    due_date datetimeoffset NULL
);
CREATE INDEX ix_cards_column_cursor ON dbo.cards(project_id, board_id, column_id, position DESC, id DESC);
CREATE TABLE dbo.task_assignees (
    task_id bigint NOT NULL,
    user_id uniqueidentifier NOT NULL,
    assigned_by_user_id uniqueidentifier NOT NULL,
    assigned_at datetimeoffset NOT NULL,
    membership_version bigint NOT NULL,
    is_primary bit NOT NULL,
    CONSTRAINT pk_task_assignees PRIMARY KEY (task_id, user_id),
    CONSTRAINT fk_task_assignees_card FOREIGN KEY (task_id) REFERENCES dbo.cards(id)
);
CREATE TABLE dbo.card_comments (
    id bigint IDENTITY(1,1) NOT NULL PRIMARY KEY,
    card_id bigint NOT NULL,
    author_id uniqueidentifier NOT NULL,
    content nvarchar(max) NOT NULL,
    created_at datetimeoffset NULL,
    CONSTRAINT fk_card_comments_card FOREIGN KEY (card_id) REFERENCES dbo.cards(id)
);
CREATE TABLE dbo.membership_assignment_fences (
    project_id bigint NOT NULL,
    user_id uniqueidentifier NOT NULL,
    membership_version bigint NOT NULL,
    event_id uniqueidentifier NOT NULL,
    fenced_at datetimeoffset NOT NULL,
    CONSTRAINT pk_membership_assignment_fences PRIMARY KEY (project_id, user_id, membership_version)
);
CREATE TABLE dbo.member_assignment_cleanup_jobs (
    job_id uniqueidentifier NOT NULL PRIMARY KEY,
    deduplication_key varchar(220) NOT NULL UNIQUE,
    event_id uniqueidentifier NOT NULL UNIQUE,
    operation_id uniqueidentifier NOT NULL,
    project_id bigint NOT NULL,
    user_id uniqueidentifier NOT NULL,
    membership_version bigint NOT NULL,
    source_aggregate_version bigint NOT NULL,
    board_ids_json nvarchar(max) NOT NULL,
    status varchar(20) NOT NULL,
    last_processed_card_id bigint NULL,
    attempt_count int NOT NULL,
    last_error nvarchar(max) NULL,
    created_at datetimeoffset NOT NULL,
    updated_at datetimeoffset NOT NULL,
    next_attempt_at datetimeoffset NOT NULL
);
CREATE INDEX idx_member_assignment_cleanup_due ON dbo.member_assignment_cleanup_jobs(status, updated_at);
GO

USE kanban_notification_db;
GO
CREATE TABLE dbo.notifications (
    id bigint IDENTITY(1,1) NOT NULL PRIMARY KEY,
    user_id uniqueidentifier NOT NULL,
    project_id bigint NULL,
    type varchar(30) NOT NULL,
    title nvarchar(500) NOT NULL,
    card_id bigint NULL,
    board_id bigint NULL,
    [read] bit NOT NULL,
    created_at datetimeoffset NULL
);
CREATE INDEX idx_notifications_user_read ON dbo.notifications(user_id, [read]);
GO

/* Each service owns its own messaging tables. These remain empty at bootstrap. */
DECLARE @db sysname, @sql nvarchar(max);
DECLARE dbs CURSOR LOCAL FAST_FORWARD FOR
    SELECT name FROM (VALUES (N'kanban_user_db'), (N'kanban_board_db'), (N'kanban_task_db'), (N'kanban_notification_db')) AS d(name);
OPEN dbs;
FETCH NEXT FROM dbs INTO @db;
WHILE @@FETCH_STATUS = 0
BEGIN
    SET @sql = N'USE ' + QUOTENAME(@db) + N';
        CREATE TABLE dbo.outbox_events (
            event_id uniqueidentifier NOT NULL PRIMARY KEY,
            event_type varchar(120) NOT NULL,
            aggregate_type varchar(80) NOT NULL,
            aggregate_id varchar(80) NOT NULL,
            aggregate_version bigint NOT NULL,
            payload nvarchar(max) NOT NULL,
            status varchar(20) NOT NULL CHECK (status IN (''PENDING'',''SENT'')),
            attempt_count int NOT NULL,
            created_at datetimeoffset NOT NULL,
            next_attempt_at datetimeoffset NOT NULL,
            sent_at datetimeoffset NULL,
            last_error nvarchar(max) NULL
        );
        CREATE INDEX idx_outbox_due ON dbo.outbox_events(status, next_attempt_at, created_at);
        CREATE TABLE dbo.inbox_events (
            consumer_name varchar(120) NOT NULL,
            event_id uniqueidentifier NOT NULL,
            event_type varchar(120) NOT NULL,
            received_at datetimeoffset NOT NULL,
            PRIMARY KEY (consumer_name, event_id)
        );';
    EXEC sys.sp_executesql @sql;
    FETCH NEXT FROM dbs INTO @db;
END;
CLOSE dbs;
DEALLOCATE dbs;
GO

/* Demo data only. All three accounts use the local-only password DemoPass123!.
   No real account should use these credentials. Login is not yet wired end-to-end.
   Seed membership generation because it is durable membership state, not a job. */
USE kanban_user_db;
GO
INSERT dbo.system_roles(role_code, description) VALUES
('USER', N'Tài khoản nghiệp vụ'), ('SYSTEM_ADMIN', N'Quản trị tài khoản hệ thống');
INSERT dbo.users(id, email, password_hash, full_name, status, auth_version) VALUES
('11111111-1111-4111-8111-111111111111', 'admin@demo.local', '$2b$12$w6.EgS/JtKQHQCjGI.ZVOeL2YELiwpQQ5Gj4pJCwWlS/mwe44mBIa', N'Quản trị viên', 'ACTIVE', 1),
('22222222-2222-4222-8222-222222222222', 'owner@demo.local', '$2b$12$w6.EgS/JtKQHQCjGI.ZVOeL2YELiwpQQ5Gj4pJCwWlS/mwe44mBIa', N'Nguyễn An', 'ACTIVE', 1),
('33333333-3333-4333-8333-333333333333', 'member@demo.local', '$2b$12$w6.EgS/JtKQHQCjGI.ZVOeL2YELiwpQQ5Gj4pJCwWlS/mwe44mBIa', N'Trần Bình', 'ACTIVE', 1);
INSERT dbo.user_system_roles(user_id, role_code, granted_at, granted_by) VALUES
('11111111-1111-4111-8111-111111111111', 'SYSTEM_ADMIN', TODATETIMEOFFSET(SYSUTCDATETIME(), '+00:00'), NULL),
('22222222-2222-4222-8222-222222222222', 'USER', TODATETIMEOFFSET(SYSUTCDATETIME(), '+00:00'), NULL),
('33333333-3333-4333-8333-333333333333', 'USER', TODATETIMEOFFSET(SYSUTCDATETIME(), '+00:00'), NULL);
GO
USE kanban_board_db;
GO
SET IDENTITY_INSERT dbo.projects ON;
INSERT dbo.projects(id, name, status, created_by_user_id, created_at, updated_at) VALUES
(1, N'Dev Platform', 'ACTIVE', '22222222-2222-4222-8222-222222222222', TODATETIMEOFFSET(SYSUTCDATETIME(), '+00:00'), TODATETIMEOFFSET(SYSUTCDATETIME(), '+00:00'));
SET IDENTITY_INSERT dbo.projects OFF;
INSERT dbo.project_memberships(project_id, user_id, role, status, joined_at, removed_at, updated_at) VALUES
(1, '22222222-2222-4222-8222-222222222222', 'OWNER', 'ACTIVE', TODATETIMEOFFSET(SYSUTCDATETIME(), '+00:00'), NULL, TODATETIMEOFFSET(SYSUTCDATETIME(), '+00:00')),
(1, '33333333-3333-4333-8333-333333333333', 'MEMBER', 'ACTIVE', TODATETIMEOFFSET(SYSUTCDATETIME(), '+00:00'), NULL, TODATETIMEOFFSET(SYSUTCDATETIME(), '+00:00'));
INSERT dbo.project_membership_versions(project_id, user_id, membership_version, authorization_version, row_version) VALUES
(1, '22222222-2222-4222-8222-222222222222', 1, 1, 0),
(1, '33333333-3333-4333-8333-333333333333', 1, 1, 0);
SET IDENTITY_INSERT dbo.invitations ON;
INSERT dbo.invitations(id, project_id, user_id, invited_by_user_id, status, created_at, updated_at, responded_at) VALUES
(1, 1, '33333333-3333-4333-8333-333333333333', '22222222-2222-4222-8222-222222222222', 'ACCEPTED', TODATETIMEOFFSET(SYSUTCDATETIME(), '+00:00'), TODATETIMEOFFSET(SYSUTCDATETIME(), '+00:00'), TODATETIMEOFFSET(SYSUTCDATETIME(), '+00:00'));
SET IDENTITY_INSERT dbo.invitations OFF;
SET IDENTITY_INSERT dbo.boards ON;
INSERT dbo.boards(id, project_id, name, board_key, status, created_by_user_id, created_at, updated_at) VALUES
(1, 1, N'Platform Roadmap', 'platform', 'ACTIVE', '22222222-2222-4222-8222-222222222222', TODATETIMEOFFSET(SYSUTCDATETIME(), '+00:00'), TODATETIMEOFFSET(SYSUTCDATETIME(), '+00:00'));
SET IDENTITY_INSERT dbo.boards OFF;
SET IDENTITY_INSERT dbo.board_columns ON;
INSERT dbo.board_columns(id, board_id, name, position) VALUES
(1, 1, N'Cần làm', 0), (2, 1, N'Đang thực hiện', 1), (3, 1, N'Hoàn thành', 2);
SET IDENTITY_INSERT dbo.board_columns OFF;
GO
USE kanban_task_db;
GO
SET IDENTITY_INSERT dbo.cards ON;
INSERT dbo.cards(id, project_id, board_id, column_id, title, description, status, position, due_date) VALUES
(1, 1, 1, 1, N'Thiết kế workspace Project', N'Thống nhất phạm vi quản lý Project.', 'TODO', 100, NULL),
(2, 1, 1, 2, N'Chuẩn bị Kanban', N'Hoàn thiện các cột và luồng làm việc.', 'IN_PROGRESS', 100, NULL);
SET IDENTITY_INSERT dbo.cards OFF;
INSERT dbo.task_assignees(task_id, user_id, assigned_by_user_id, assigned_at, membership_version, is_primary) VALUES
(2, '33333333-3333-4333-8333-333333333333', '22222222-2222-4222-8222-222222222222', TODATETIMEOFFSET(SYSUTCDATETIME(), '+00:00'), 1, 1);
SET IDENTITY_INSERT dbo.card_comments ON;
INSERT dbo.card_comments(id, card_id, author_id, content, created_at) VALUES
(1, 2, '33333333-3333-4333-8333-333333333333', N'Em đang chuẩn bị bảng Kanban.', TODATETIMEOFFSET(SYSUTCDATETIME(), '+00:00'));
SET IDENTITY_INSERT dbo.card_comments OFF;
GO
USE kanban_notification_db;
GO
SET IDENTITY_INSERT dbo.notifications ON;
INSERT dbo.notifications(id, user_id, project_id, type, title, card_id, board_id, [read], created_at) VALUES
(1, '33333333-3333-4333-8333-333333333333', 1, 'ASSIGNED', N'Bạn được giao công việc Chuẩn bị Kanban', 2, 1, 0, TODATETIMEOFFSET(SYSUTCDATETIME(), '+00:00'));
SET IDENTITY_INSERT dbo.notifications OFF;
COMMIT TRANSACTION;
GO
