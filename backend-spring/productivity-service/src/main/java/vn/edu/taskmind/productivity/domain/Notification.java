package vn.edu.taskmind.productivity.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "notifications")
public class Notification {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "NotificationID", nullable = false)
    private Integer notificationId;

    @Column(name = "UserID", nullable = false)
    private Integer userId;

    @Column(name = "ReminderID", nullable = true)
    private Integer reminderId;

    @Column(name = "Title", nullable = false, length = 255)
    private String title;

    @Column(name = "Content", nullable = true, columnDefinition = "text")
    private String content;

    @Column(name = "IsRead", nullable = false, columnDefinition = "tinyint(1)")
    private Boolean isRead = false;

    @Column(name = "Type", nullable = false, length = 20,
            columnDefinition = "enum('INFO','SUCCESS','WARNING','ERROR','REMINDER')")
    private String type = "INFO";

    @Column(name = "CreatedAt", nullable = false, columnDefinition = "datetime")
    private LocalDateTime createdAt;

    @Column(name = "ReadAt", nullable = true, columnDefinition = "datetime")
    private LocalDateTime readAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "ReminderID", insertable = false, updatable = false)
    private Reminder reminder;

    public Notification() { }

    public Integer getNotificationId() { return notificationId; }
    public void setNotificationId(Integer notificationId) { this.notificationId = notificationId; }
    public Integer getUserId() { return userId; }
    public void setUserId(Integer userId) { this.userId = userId; }
    public Integer getReminderId() { return reminderId; }
    public void setReminderId(Integer reminderId) { this.reminderId = reminderId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public Boolean getIsRead() { return isRead; }
    public void setIsRead(Boolean isRead) { this.isRead = isRead; }
    public String getType() { return type; }
    public void setType(String value) { type = value; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getReadAt() { return readAt; }
    public void setReadAt(LocalDateTime value) { readAt = value; }
    public Reminder getReminder() { return reminder; }
}
