CREATE TABLE aichats (
    ChatID INT NOT NULL AUTO_INCREMENT,
    UserID INT NOT NULL,
    Title VARCHAR(200) NOT NULL,
    CreatedAt DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UpdatedAt DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (ChatID),
    INDEX ix_aichats_user (UserID),
    CONSTRAINT ck_aichats_user CHECK (UserID > 0),
    CONSTRAINT ck_aichats_title CHECK (CHAR_LENGTH(TRIM(Title)) > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE aichatmessages (
    MessageID BIGINT NOT NULL AUTO_INCREMENT,
    ChatID INT NOT NULL,
    Role VARCHAR(20) NOT NULL,
    Content TEXT NOT NULL,
    CreatedAt DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (MessageID),
    INDEX ix_aichatmessages_chat_created (ChatID, CreatedAt),
    CONSTRAINT fk_aichatmessages_chat FOREIGN KEY (ChatID) REFERENCES aichats (ChatID) ON DELETE RESTRICT,
    CONSTRAINT ck_aichatmessages_role CHECK (CHAR_LENGTH(TRIM(Role)) > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
