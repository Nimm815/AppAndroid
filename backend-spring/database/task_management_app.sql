-- Tạo database và các bảng theo schema đã gửi.
-- Lưu ý: đây là script khởi tạo rỗng, không chứa INSERT dữ liệu.
-- Chỉ chạy trên database mới hoặc sau khi đã sao lưu dữ liệu cần giữ.

CREATE DATABASE IF NOT EXISTS `task_management_app`
  DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE `task_management_app`;

CREATE TABLE IF NOT EXISTS `users` (
  `UserID` int unsigned NOT NULL AUTO_INCREMENT,
  `FullName` varchar(100) NOT NULL,
  `Email` varchar(255) NOT NULL,
  `PasswordHash` varchar(255) NOT NULL,
  `Avatar` varchar(500) DEFAULT NULL,
  `Role` enum('USER','ADMIN') NOT NULL DEFAULT 'USER',
  `IsLocked` tinyint(1) NOT NULL DEFAULT '0',
  `IsDeleted` tinyint(1) NOT NULL DEFAULT '0',
  `CreatedAt` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `UpdatedAt` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`UserID`), UNIQUE KEY `Email` (`Email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `tasks` (
  `TaskID` int unsigned NOT NULL AUTO_INCREMENT,
  `UserID` int unsigned NOT NULL,
  `Title` varchar(200) NOT NULL,
  `Description` text,
  `Priority` enum('LOW','MEDIUM','HIGH','URGENT') NOT NULL DEFAULT 'MEDIUM',
  `Status` enum('TODO','IN_PROGRESS','COMPLETED','CANCELLED') NOT NULL DEFAULT 'TODO',
  `Progress` tinyint unsigned NOT NULL DEFAULT '0',
  `StartDate` datetime DEFAULT NULL,
  `DueDate` datetime DEFAULT NULL,
  `CompletedAt` datetime DEFAULT NULL,
  `CreatedAt` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `UpdatedAt` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`TaskID`), KEY `idx_tasks_user_status` (`UserID`,`Status`), KEY `idx_tasks_due_date` (`DueDate`),
  CONSTRAINT `fk_tasks_user` FOREIGN KEY (`UserID`) REFERENCES `users` (`UserID`) ON DELETE RESTRICT ON UPDATE CASCADE,
  CONSTRAINT `chk_tasks_progress` CHECK (`Progress` between 0 and 100)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `tasklists` (
  `ListID` int unsigned NOT NULL AUTO_INCREMENT, `UserID` int unsigned NOT NULL,
  `ListName` varchar(100) NOT NULL, `Description` text, `Color` varchar(20) DEFAULT NULL,
  `CreatedAt` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `UpdatedAt` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`ListID`), KEY `idx_tasklists_user` (`UserID`),
  CONSTRAINT `fk_tasklists_user` FOREIGN KEY (`UserID`) REFERENCES `users` (`UserID`) ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `tasklistitems` (
  `ListID` int unsigned NOT NULL, `TaskID` int unsigned NOT NULL,
  `AddedAt` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP, `SortOrder` int NOT NULL DEFAULT '0',
  PRIMARY KEY (`ListID`,`TaskID`), KEY `fk_tasklistitems_task` (`TaskID`),
  CONSTRAINT `fk_tasklistitems_list` FOREIGN KEY (`ListID`) REFERENCES `tasklists` (`ListID`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `fk_tasklistitems_task` FOREIGN KEY (`TaskID`) REFERENCES `tasks` (`TaskID`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `habits` (
  `HabitID` int unsigned NOT NULL AUTO_INCREMENT, `UserID` int unsigned NOT NULL,
  `HabitName` varchar(200) NOT NULL, `Description` text,
  `Frequency` enum('DAILY','WEEKLY','CUSTOM') NOT NULL DEFAULT 'DAILY',
  `StartDate` date NOT NULL, `EndDate` date DEFAULT NULL, `TargetCount` int unsigned NOT NULL DEFAULT '1',
  `IsActive` tinyint(1) NOT NULL DEFAULT '1', `CreatedAt` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `UpdatedAt` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`HabitID`), KEY `idx_habits_user_active` (`UserID`,`IsActive`),
  CONSTRAINT `fk_habits_user` FOREIGN KEY (`UserID`) REFERENCES `users` (`UserID`) ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `habitlogs` (
  `HabitLogID` bigint unsigned NOT NULL AUTO_INCREMENT, `HabitID` int unsigned NOT NULL, `LogDate` date NOT NULL,
  `Status` enum('COMPLETED','PARTIAL','MISSED','SKIPPED') NOT NULL DEFAULT 'MISSED',
  `ActualCount` int unsigned NOT NULL DEFAULT '0', `Note` text,
  `CreatedAt` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `UpdatedAt` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`HabitLogID`), UNIQUE KEY `uq_habitlogs_habit_date` (`HabitID`,`LogDate`),
  CONSTRAINT `fk_habitlogs_habit` FOREIGN KEY (`HabitID`) REFERENCES `habits` (`HabitID`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `journalentries` (
  `EntryID` bigint unsigned NOT NULL AUTO_INCREMENT, `UserID` int unsigned NOT NULL,
  `Title` varchar(200) NOT NULL, `Content` text NOT NULL, `EntryDate` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `IsPinned` tinyint(1) NOT NULL DEFAULT '0', `CreatedAt` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `UpdatedAt` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`EntryID`), KEY `idx_journal_user_date` (`UserID`,`EntryDate`), KEY `idx_journal_user_pinned` (`UserID`,`IsPinned`),
  CONSTRAINT `fk_journalentries_user` FOREIGN KEY (`UserID`) REFERENCES `users` (`UserID`) ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `calendarevents` (
  `EventID` int unsigned NOT NULL AUTO_INCREMENT, `UserID` int unsigned NOT NULL,
  `Title` varchar(200) NOT NULL, `Description` text, `StartTime` datetime NOT NULL, `EndTime` datetime NOT NULL,
  `Location` varchar(255) DEFAULT NULL, `IsAllDay` tinyint(1) NOT NULL DEFAULT '0', `Color` varchar(20) DEFAULT NULL,
  `CreatedAt` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `UpdatedAt` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`EventID`), KEY `idx_events_user_start` (`UserID`,`StartTime`),
  CONSTRAINT `fk_calendarevents_user` FOREIGN KEY (`UserID`) REFERENCES `users` (`UserID`) ON DELETE RESTRICT ON UPDATE CASCADE,
  CONSTRAINT `chk_calendar_event_time` CHECK (`EndTime` >= `StartTime`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `reminders` (
  `ReminderID` int unsigned NOT NULL AUTO_INCREMENT, `UserID` int unsigned NOT NULL,
  `TaskID` int unsigned DEFAULT NULL, `EventID` int unsigned DEFAULT NULL, `Title` varchar(255) NOT NULL,
  `RemindAt` datetime NOT NULL, `RepeatRule` varchar(100) DEFAULT NULL, `IsEnabled` tinyint(1) NOT NULL DEFAULT '1',
  `CreatedAt` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `UpdatedAt` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`ReminderID`), KEY `fk_reminders_user` (`UserID`), KEY `fk_reminders_task` (`TaskID`), KEY `fk_reminders_event` (`EventID`),
  CONSTRAINT `fk_reminders_event` FOREIGN KEY (`EventID`) REFERENCES `calendarevents` (`EventID`),
  CONSTRAINT `fk_reminders_task` FOREIGN KEY (`TaskID`) REFERENCES `tasks` (`TaskID`),
  CONSTRAINT `fk_reminders_user` FOREIGN KEY (`UserID`) REFERENCES `users` (`UserID`),
  CONSTRAINT `chk_reminders_single_target` CHECK (`TaskID` IS NULL OR `EventID` IS NULL)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `notifications` (
  `NotificationID` int unsigned NOT NULL AUTO_INCREMENT, `UserID` int unsigned NOT NULL,
  `ReminderID` int unsigned DEFAULT NULL, `Title` varchar(255) NOT NULL, `Content` text,
  `Type` enum('INFO','SUCCESS','WARNING','ERROR','REMINDER') NOT NULL DEFAULT 'INFO',
  `IsRead` tinyint(1) NOT NULL DEFAULT '0', `CreatedAt` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP, `ReadAt` datetime DEFAULT NULL,
  PRIMARY KEY (`NotificationID`), KEY `fk_notifications_user` (`UserID`), KEY `fk_notifications_reminder` (`ReminderID`),
  CONSTRAINT `fk_notifications_reminder` FOREIGN KEY (`ReminderID`) REFERENCES `reminders` (`ReminderID`) ON DELETE SET NULL,
  CONSTRAINT `fk_notifications_user` FOREIGN KEY (`UserID`) REFERENCES `users` (`UserID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `airecommendations` (
  `RecommendationID` int unsigned NOT NULL AUTO_INCREMENT, `UserID` int unsigned NOT NULL,
  `RequestText` text, `ContextSnapshot` json DEFAULT NULL, `ModelName` varchar(100) DEFAULT NULL,
  `Status` enum('PENDING','SUCCESS','FAILED') NOT NULL DEFAULT 'PENDING',
  `CreatedAt` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP, `CompletedAt` datetime DEFAULT NULL,
  PRIMARY KEY (`RecommendationID`), KEY `fk_airecommendations_user` (`UserID`),
  CONSTRAINT `fk_airecommendations_user` FOREIGN KEY (`UserID`) REFERENCES `users` (`UserID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `airecommendationitems` (
  `RecommendationItemID` int unsigned NOT NULL AUTO_INCREMENT, `RecommendationID` int unsigned NOT NULL,
  `TaskID` int unsigned DEFAULT NULL, `SuggestedOrder` int NOT NULL, `Reason` text, `Score` decimal(5,4) DEFAULT NULL,
  `CreatedAt` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`RecommendationItemID`), UNIQUE KEY `RecommendationID` (`RecommendationID`,`SuggestedOrder`),
  KEY `fk_airecommendationitems_task` (`TaskID`),
  CONSTRAINT `fk_airecommendationitems_recommendation` FOREIGN KEY (`RecommendationID`) REFERENCES `airecommendations` (`RecommendationID`) ON DELETE CASCADE,
  CONSTRAINT `fk_airecommendationitems_task` FOREIGN KEY (`TaskID`) REFERENCES `tasks` (`TaskID`) ON DELETE SET NULL,
  CONSTRAINT `airecommendationitems_chk_1` CHECK (`Score` IS NULL OR (`Score` >= 0 AND `Score` <= 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `activitylogs` (
  `ActivityLogID` bigint unsigned NOT NULL AUTO_INCREMENT, `UserID` int unsigned DEFAULT NULL,
  `Action` varchar(100) NOT NULL, `EntityType` varchar(100) DEFAULT NULL, `EntityID` varchar(100) DEFAULT NULL,
  `Details` json DEFAULT NULL, `IPAddress` varchar(45) DEFAULT NULL, `CreatedAt` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`ActivityLogID`), KEY `fk_activitylogs_user` (`UserID`),
  CONSTRAINT `fk_activitylogs_user` FOREIGN KEY (`UserID`) REFERENCES `users` (`UserID`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
