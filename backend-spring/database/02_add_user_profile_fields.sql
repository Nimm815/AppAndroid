-- Chạy trong MySQL Workbench. Script chỉ bổ sung cột còn thiếu trên
-- taskmind_auth_db.users; chạy lại an toàn nếu một vài cột đã tồn tại.
SET @profile_schema = 'taskmind_auth_db';
SET @profile_table = 'users';

SET @profile_sql = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = @profile_schema AND TABLE_NAME = @profile_table AND COLUMN_NAME = 'PhoneNumber') = 0,
    'ALTER TABLE taskmind_auth_db.users ADD COLUMN PhoneNumber VARCHAR(20) NULL',
    'SELECT ''PhoneNumber already exists'''
);
PREPARE profile_stmt FROM @profile_sql;
EXECUTE profile_stmt;
DEALLOCATE PREPARE profile_stmt;

SET @profile_sql = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = @profile_schema AND TABLE_NAME = @profile_table AND COLUMN_NAME = 'BirthDate') = 0,
    'ALTER TABLE taskmind_auth_db.users ADD COLUMN BirthDate DATE NULL',
    'SELECT ''BirthDate already exists'''
);
PREPARE profile_stmt FROM @profile_sql;
EXECUTE profile_stmt;
DEALLOCATE PREPARE profile_stmt;

SET @profile_sql = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = @profile_schema AND TABLE_NAME = @profile_table AND COLUMN_NAME = 'Gender') = 0,
    'ALTER TABLE taskmind_auth_db.users ADD COLUMN Gender VARCHAR(10) NULL',
    'SELECT ''Gender already exists'''
);
PREPARE profile_stmt FROM @profile_sql;
EXECUTE profile_stmt;
DEALLOCATE PREPARE profile_stmt;

-- Xác nhận các cột đã có trong đúng database.
SELECT TABLE_SCHEMA, TABLE_NAME, COLUMN_NAME, COLUMN_TYPE
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = @profile_schema AND TABLE_NAME = @profile_table
  AND COLUMN_NAME IN ('PhoneNumber', 'BirthDate', 'Gender')
ORDER BY ORDINAL_POSITION;
