CREATE TABLE habits (
    HabitID INT NOT NULL AUTO_INCREMENT,
    UserID INT NOT NULL,
    HabitName VARCHAR(200) NOT NULL,
    Frequency VARCHAR(20) NOT NULL,
    TargetCount INT NOT NULL DEFAULT 1,
    IsActive TINYINT(1) NOT NULL DEFAULT 1,
    PRIMARY KEY (HabitID),
    INDEX ix_habits_user (UserID),
    CONSTRAINT ck_habits_user CHECK (UserID > 0),
    CONSTRAINT ck_habits_name CHECK (CHAR_LENGTH(TRIM(HabitName)) > 0),
    CONSTRAINT ck_habits_frequency CHECK (CHAR_LENGTH(TRIM(Frequency)) > 0),
    CONSTRAINT ck_habits_target CHECK (TargetCount > 0),
    CONSTRAINT ck_habits_active CHECK (IsActive IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE habitlogs (
    HabitLogID BIGINT NOT NULL AUTO_INCREMENT,
    HabitID INT NOT NULL,
    LogDate DATE NOT NULL,
    Status VARCHAR(20) NOT NULL,
    ActualCount INT NOT NULL DEFAULT 0,
    PRIMARY KEY (HabitLogID),
    CONSTRAINT uq_habitlogs_day UNIQUE (HabitID, LogDate),
    CONSTRAINT fk_habitlogs_habit FOREIGN KEY (HabitID) REFERENCES habits (HabitID) ON DELETE RESTRICT,
    CONSTRAINT ck_habitlogs_count CHECK (ActualCount >= 0),
    CONSTRAINT ck_habitlogs_status CHECK (CHAR_LENGTH(TRIM(Status)) > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
