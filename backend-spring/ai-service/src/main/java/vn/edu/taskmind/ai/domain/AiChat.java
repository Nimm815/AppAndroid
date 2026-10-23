package vn.edu.taskmind.ai.domain;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Entity
@Table(name = "aichats")
public class AiChat {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ChatID", nullable = false)
    private Integer chatId;

    @Column(name = "UserID", nullable = false)
    private Integer userId;

    @Column(name = "Title", nullable = false, length = 200)
    private String title;

    @Column(name = "CreatedAt", nullable = false, columnDefinition = "datetime")
    private LocalDateTime createdAt;

    @Column(name = "UpdatedAt", nullable = false, columnDefinition = "datetime")
    private LocalDateTime updatedAt;

    public AiChat() { }

    public Integer getChatId() { return chatId; }
    public void setChatId(Integer chatId) { this.chatId = chatId; }
    public Integer getUserId() { return userId; }
    public void setUserId(Integer userId) { this.userId = userId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    @PrePersist
    private void initializeTimestamps() {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
    }

    @PreUpdate
    private void updateTimestamp() { updatedAt = LocalDateTime.now(ZoneOffset.UTC); }
}
