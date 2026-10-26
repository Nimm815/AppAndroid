CREATE TABLE calendarevents (
    EventID INT NOT NULL AUTO_INCREMENT,
    UserID INT NOT NULL,
    Title VARCHAR(200) NOT NULL,
    StartTime DATETIME NOT NULL,
    EndTime DATETIME NOT NULL,
    Location VARCHAR(200) NULL,
    PRIMARY KEY (EventID),
    INDEX ix_calendarevents_user_start (UserID, StartTime),
    CONSTRAINT ck_calendarevents_user CHECK (UserID > 0),
    CONSTRAINT ck_calendarevents_title CHECK (CHAR_LENGTH(TRIM(Title)) > 0),
    CONSTRAINT ck_calendarevents_time CHECK (EndTime >= StartTime)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE journalentries (
    EntryID BIGINT NOT NULL AUTO_INCREMENT,
    UserID INT NOT NULL,
    Title VARCHAR(200) NOT NULL,
    Content TEXT NOT NULL,
    EntryDate DATETIME NOT NULL,
    PRIMARY KEY (EntryID),
    INDEX ix_journalentries_user_date (UserID, EntryDate),
    CONSTRAINT ck_journalentries_user CHECK (UserID > 0),
    CONSTRAINT ck_journalentries_title CHECK (CHAR_LENGTH(TRIM(Title)) > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE reminders (
    ReminderID INT NOT NULL AUTO_INCREMENT,
    UserID INT NOT NULL,
    TaskID INT NULL,
    EventID INT NULL,
    RemindAt DATETIME NOT NULL,
    IsEnabled TINYINT(1) NOT NULL DEFAULT 1,
    PRIMARY KEY (ReminderID),
    INDEX ix_reminders_user (UserID),
    INDEX ix_reminders_task (TaskID),
    INDEX ix_reminders_due (IsEnabled, RemindAt),
    CONSTRAINT fk_reminders_event FOREIGN KEY (EventID) REFERENCES calendarevents (EventID) ON DELETE RESTRICT,
    CONSTRAINT ck_reminders_user CHECK (UserID > 0),
    CONSTRAINT ck_reminders_task CHECK (TaskID IS NULL OR TaskID > 0),
    CONSTRAINT ck_reminders_target CHECK ((TaskID IS NOT NULL AND EventID IS NULL) OR (TaskID IS NULL AND EventID IS NOT NULL)),
    CONSTRAINT ck_reminders_enabled CHECK (IsEnabled IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE notifications (
    NotificationID INT NOT NULL AUTO_INCREMENT,
    UserID INT NOT NULL,
    ReminderID INT NOT NULL,
    Title VARCHAR(200) NOT NULL,
    Content TEXT NOT NULL,
    IsRead TINYINT(1) NOT NULL DEFAULT 0,
    PRIMARY KEY (NotificationID),
    INDEX ix_notifications_user_read (UserID, IsRead),
    CONSTRAINT fk_notifications_reminder FOREIGN KEY (ReminderID) REFERENCES reminders (ReminderID) ON DELETE RESTRICT,
    CONSTRAINT ck_notifications_user CHECK (UserID > 0),
    CONSTRAINT ck_notifications_title CHECK (CHAR_LENGTH(TRIM(Title)) > 0),
    CONSTRAINT ck_notifications_read CHECK (IsRead IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
