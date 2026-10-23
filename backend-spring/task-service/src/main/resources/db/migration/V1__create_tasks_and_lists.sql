CREATE TABLE tasks (
    TaskID INT NOT NULL AUTO_INCREMENT,
    UserID INT NOT NULL,
    Title VARCHAR(200) NOT NULL,
    Priority VARCHAR(20) NOT NULL,
    Status VARCHAR(20) NOT NULL,
    Progress TINYINT NOT NULL DEFAULT 0,
    DueDate DATETIME NULL,
    CompletedAt DATETIME NULL,
    PRIMARY KEY (TaskID),
    INDEX ix_tasks_user_due (UserID, DueDate),
    CONSTRAINT ck_tasks_user CHECK (UserID > 0),
    CONSTRAINT ck_tasks_title CHECK (CHAR_LENGTH(TRIM(Title)) > 0),
    CONSTRAINT ck_tasks_priority CHECK (CHAR_LENGTH(TRIM(Priority)) > 0),
    CONSTRAINT ck_tasks_status CHECK (CHAR_LENGTH(TRIM(Status)) > 0),
    CONSTRAINT ck_tasks_progress CHECK (Progress BETWEEN 0 AND 100)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE tasklists (
    ListID INT NOT NULL AUTO_INCREMENT,
    UserID INT NOT NULL,
    ListName VARCHAR(100) NOT NULL,
    Color VARCHAR(20) NULL,
    CreatedAt DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (ListID),
    INDEX ix_tasklists_user (UserID),
    CONSTRAINT ck_tasklists_user CHECK (UserID > 0),
    CONSTRAINT ck_tasklists_name CHECK (CHAR_LENGTH(TRIM(ListName)) > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE tasklistitems (
    ListID INT NOT NULL,
    TaskID INT NOT NULL,
    SortOrder INT NOT NULL DEFAULT 0,
    PRIMARY KEY (ListID, TaskID),
    CONSTRAINT uq_tasklistitems_task UNIQUE (TaskID),
    CONSTRAINT fk_tasklistitems_list FOREIGN KEY (ListID) REFERENCES tasklists (ListID) ON DELETE CASCADE,
    CONSTRAINT fk_tasklistitems_task FOREIGN KEY (TaskID) REFERENCES tasks (TaskID) ON DELETE CASCADE,
    CONSTRAINT ck_tasklistitems_order CHECK (SortOrder >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
