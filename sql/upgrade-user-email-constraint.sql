USE kanban_user_db;
GO

SET XACT_ABORT ON;

IF OBJECT_ID(N'dbo.users', N'U') IS NULL
    THROW 50002, 'Upgrade requires the existing dbo.users table.', 1;

BEGIN TRANSACTION;

IF EXISTS (
    SELECT 1
    FROM sys.key_constraints kc
    WHERE kc.parent_object_id = OBJECT_ID(N'dbo.users')
      AND kc.name = N'uq_users_email'
      AND kc.type = 'UQ'
)
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM sys.key_constraints kc
        JOIN sys.index_columns ic
          ON ic.object_id = kc.parent_object_id
         AND ic.index_id = kc.unique_index_id
         AND ic.key_ordinal = 1
        JOIN sys.columns c
          ON c.object_id = ic.object_id
         AND c.column_id = ic.column_id
        WHERE kc.parent_object_id = OBJECT_ID(N'dbo.users')
          AND kc.name = N'uq_users_email'
          AND kc.type = 'UQ'
          AND c.name = N'email'
          AND 1 = (
              SELECT COUNT(*)
              FROM sys.index_columns key_column
              WHERE key_column.object_id = kc.parent_object_id
                AND key_column.index_id = kc.unique_index_id
                AND key_column.key_ordinal > 0
          )
    )
        THROW 50003, 'uq_users_email exists but does not constrain only dbo.users.email.', 1;
END

DECLARE @oldEmailConstraint sysname;
WHILE 1 = 1
BEGIN
    SET @oldEmailConstraint = NULL;
    SELECT TOP (1) @oldEmailConstraint = kc.name
    FROM sys.key_constraints kc
    WHERE kc.parent_object_id = OBJECT_ID(N'dbo.users')
      AND kc.type = 'UQ'
      AND kc.name <> N'uq_users_email'
      AND EXISTS (
          SELECT 1
          FROM sys.index_columns ic
          JOIN sys.columns c
            ON c.object_id = ic.object_id
           AND c.column_id = ic.column_id
          WHERE ic.object_id = kc.parent_object_id
            AND ic.index_id = kc.unique_index_id
            AND ic.key_ordinal = 1
            AND c.name = N'email'
      )
      AND 1 = (
          SELECT COUNT(*)
          FROM sys.index_columns key_column
          WHERE key_column.object_id = kc.parent_object_id
            AND key_column.index_id = kc.unique_index_id
            AND key_column.key_ordinal > 0
      );

    IF @oldEmailConstraint IS NULL BREAK;

    DECLARE @dropConstraintSql nvarchar(max) =
        N'ALTER TABLE dbo.users DROP CONSTRAINT ' + QUOTENAME(@oldEmailConstraint) + N';';
    EXEC sys.sp_executesql @dropConstraintSql;
END;

IF NOT EXISTS (
    SELECT 1
    FROM sys.key_constraints kc
    WHERE kc.parent_object_id = OBJECT_ID(N'dbo.users')
      AND kc.name = N'uq_users_email'
      AND kc.type = 'UQ'
)
BEGIN
    IF EXISTS (
        SELECT email
        FROM dbo.users
        GROUP BY email
        HAVING COUNT_BIG(*) > 1
    )
        THROW 50004, 'Cannot add uq_users_email because duplicate email values exist.', 1;

    ALTER TABLE dbo.users
      ADD CONSTRAINT uq_users_email UNIQUE (email);
END;

COMMIT TRANSACTION;
