ALTER TABLE habits
    ADD COLUMN TargetUnit VARCHAR(20) NOT NULL DEFAULT 'lần',
    ADD COLUMN ReminderTime TIME NULL,
    ADD COLUMN ReminderEnabled TINYINT(1) NOT NULL DEFAULT 0,
    ADD CONSTRAINT ck_habits_reminder CHECK (ReminderEnabled IN (0, 1));
