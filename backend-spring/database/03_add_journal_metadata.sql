-- Run once in MySQL Workbench against taskmind_productivity_db.
-- Conditional DDL preserves current records and allows safely rerunning this migration.
SET @journal_schema = 'taskmind_productivity_db';
SET @journal_table = 'journalentries';

SET @journal_sql = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = @journal_schema AND TABLE_NAME = @journal_table AND COLUMN_NAME = 'Label') = 0,
    'ALTER TABLE taskmind_productivity_db.journalentries ADD COLUMN Label VARCHAR(100) NULL',
    'SELECT ''Label already exists'''
);
PREPARE journal_stmt FROM @journal_sql;
EXECUTE journal_stmt;
DEALLOCATE PREPARE journal_stmt;

SET @journal_sql = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = @journal_schema AND TABLE_NAME = @journal_table AND COLUMN_NAME = 'SourceID') = 0,
    'ALTER TABLE taskmind_productivity_db.journalentries ADD COLUMN SourceID VARCHAR(100) NULL',
    'SELECT ''SourceID already exists'''
);
PREPARE journal_stmt FROM @journal_sql;
EXECUTE journal_stmt;
DEALLOCATE PREPARE journal_stmt;

SET @journal_sql = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = @journal_schema AND TABLE_NAME = @journal_table AND COLUMN_NAME = 'SourceType') = 0,
    'ALTER TABLE taskmind_productivity_db.journalentries ADD COLUMN SourceType VARCHAR(30) NULL',
    'SELECT ''SourceType already exists'''
);
PREPARE journal_stmt FROM @journal_sql;
EXECUTE journal_stmt;
DEALLOCATE PREPARE journal_stmt;

SET @journal_sql = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = @journal_schema AND TABLE_NAME = @journal_table AND COLUMN_NAME = 'Progress') = 0,
    'ALTER TABLE taskmind_productivity_db.journalentries ADD COLUMN Progress INT NULL',
    'SELECT ''Progress already exists'''
);
PREPARE journal_stmt FROM @journal_sql;
EXECUTE journal_stmt;
DEALLOCATE PREPARE journal_stmt;

SHOW COLUMNS FROM taskmind_productivity_db.journalentries;
