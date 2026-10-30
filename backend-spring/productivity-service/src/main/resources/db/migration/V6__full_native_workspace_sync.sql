ALTER TABLE journalentries
    ADD COLUMN IsBookmarked TINYINT(1) NOT NULL DEFAULT 0,
    ADD COLUMN Label VARCHAR(200) NOT NULL DEFAULT '',
    ADD COLUMN SourceType VARCHAR(16) NOT NULL DEFAULT '',
    ADD COLUMN SourceLocalID VARCHAR(100) NOT NULL DEFAULT '',
    ADD COLUMN Progress SMALLINT NOT NULL DEFAULT -1,
    ADD CONSTRAINT ck_journalentries_bookmarked CHECK (IsBookmarked IN (0, 1)),
    ADD CONSTRAINT ck_journalentries_progress CHECK (Progress BETWEEN -1 AND 100);

ALTER TABLE calendarevents
    ADD COLUMN Description TEXT NULL,
    ADD COLUMN IsAllDay TINYINT(1) NOT NULL DEFAULT 0,
    ADD COLUMN RelatedHabitID INT NULL,
    ADD INDEX ix_calendarevents_habit (RelatedHabitID),
    ADD CONSTRAINT fk_calendarevents_habit FOREIGN KEY (RelatedHabitID)
        REFERENCES habits (HabitID) ON DELETE RESTRICT,
    ADD CONSTRAINT ck_calendarevents_all_day CHECK (IsAllDay IN (0, 1));

ALTER TABLE reminders
    DROP CHECK ck_reminders_target,
    ADD COLUMN TargetType VARCHAR(16) NOT NULL DEFAULT 'event',
    ADD COLUMN TargetLocalID VARCHAR(100) NULL,
    ADD COLUMN TargetName VARCHAR(200) NOT NULL DEFAULT '',
    ADD COLUMN RepeatType VARCHAR(16) NOT NULL DEFAULT 'once',
    ADD COLUMN RepeatDays INT NOT NULL DEFAULT 0,
    ADD COLUMN EventReminderMinutes SMALLINT NOT NULL DEFAULT 0,
    ADD COLUMN CreatedAt DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD CONSTRAINT ck_reminders_repeat_type CHECK (RepeatType IN ('once', 'daily', 'weekdays')),
    ADD CONSTRAINT ck_reminders_repeat_days CHECK (RepeatDays BETWEEN 0 AND 127),
    ADD CONSTRAINT ck_reminders_event_minutes CHECK (EventReminderMinutes IN (0, 15));

UPDATE reminders SET TargetType = CASE WHEN TaskID IS NOT NULL THEN 'task' ELSE 'event' END;

CREATE TABLE daily_activity_history (
    ActivityID BIGINT NOT NULL AUTO_INCREMENT,
    UserID INT NOT NULL,
    DeviceID VARCHAR(36) NOT NULL,
    ActivityKey VARCHAR(200) NOT NULL,
    ActivityDate DATE NOT NULL,
    Kind VARCHAR(16) NOT NULL,
    SourceID VARCHAR(100) NOT NULL,
    Title VARCHAR(200) NOT NULL,
    Note TEXT NOT NULL,
    Progress TINYINT NOT NULL,
    IsBookmarked TINYINT(1) NOT NULL DEFAULT 0,
    PRIMARY KEY (ActivityID),
    UNIQUE KEY uq_daily_activity_owner_key (UserID, DeviceID, ActivityKey),
    KEY ix_daily_activity_user_date (UserID, ActivityDate),
    CONSTRAINT ck_daily_activity_progress CHECK (Progress BETWEEN 0 AND 100),
    CONSTRAINT ck_daily_activity_bookmarked CHECK (IsBookmarked IN (0, 1)),
    CONSTRAINT fk_daily_activity_device FOREIGN KEY (UserID, DeviceID)
        REFERENCES device_workspaces (UserID, DeviceID) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
