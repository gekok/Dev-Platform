/* Local-only schema and demo account for user-service. Run against a fresh local SQL Server. */
USE master;
GO
IF DB_ID(N'kanban_user_db') IS NULL CREATE DATABASE kanban_user_db;
GO
USE kanban_user_db;
GO
IF EXISTS (SELECT 1 FROM sys.tables)
    THROW 50001, 'user-service bootstrap requires an empty kanban_user_db.', 1;
GO
SET XACT_ABORT ON;
BEGIN TRANSACTION;

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
CREATE TABLE dbo.outbox_events (
    event_id uniqueidentifier NOT NULL PRIMARY KEY,
    event_type varchar(120) NOT NULL,
    aggregate_type varchar(80) NOT NULL,
    aggregate_id varchar(80) NOT NULL,
    aggregate_version bigint NOT NULL,
    payload nvarchar(max) NOT NULL,
    status varchar(20) NOT NULL CHECK (status IN ('PENDING','SENT')),
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
);

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

COMMIT TRANSACTION;
GO
