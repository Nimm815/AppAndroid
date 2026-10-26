-- Chỉ đọc: chạy trước và sau migration, không cần chọn default schema.
SELECT VERSION();
SELECT TABLE_SCHEMA, TABLE_NAME FROM information_schema.TABLES
WHERE TABLE_SCHEMA IN ('taskmind_auth_db', 'taskmind_task_db', 'taskmind_productivity_db', 'taskmind_ai_db')
ORDER BY TABLE_SCHEMA, TABLE_NAME;
SELECT TABLE_SCHEMA, TABLE_NAME, COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_DEFAULT
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA IN ('taskmind_auth_db', 'taskmind_task_db', 'taskmind_productivity_db', 'taskmind_ai_db')
ORDER BY TABLE_SCHEMA, TABLE_NAME, ORDINAL_POSITION;
SELECT CONSTRAINT_SCHEMA, TABLE_NAME, CONSTRAINT_NAME, CONSTRAINT_TYPE
FROM information_schema.TABLE_CONSTRAINTS
WHERE CONSTRAINT_SCHEMA IN ('taskmind_auth_db', 'taskmind_task_db', 'taskmind_productivity_db', 'taskmind_ai_db');
-- Kết quả phải rỗng: không được có FK xuyên database.
SELECT TABLE_SCHEMA, TABLE_NAME, COLUMN_NAME, REFERENCED_TABLE_SCHEMA, REFERENCED_TABLE_NAME
FROM information_schema.KEY_COLUMN_USAGE
WHERE TABLE_SCHEMA IN ('taskmind_auth_db', 'taskmind_task_db', 'taskmind_productivity_db', 'taskmind_ai_db')
AND REFERENCED_TABLE_SCHEMA IS NOT NULL AND REFERENCED_TABLE_SCHEMA <> TABLE_SCHEMA;
