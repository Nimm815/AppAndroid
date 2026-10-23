ALTER TABLE tasks ADD COLUMN Description TEXT NULL, ADD COLUMN Notes TEXT NULL, ADD COLUMN StartDate DATETIME NULL;
ALTER TABLE tasks ADD CONSTRAINT ck_tasks_dates CHECK (StartDate IS NULL OR DueDate IS NULL OR DueDate >= StartDate);
CREATE INDEX ix_tasks_owner_status_due ON tasks (UserID, Status, DueDate);
