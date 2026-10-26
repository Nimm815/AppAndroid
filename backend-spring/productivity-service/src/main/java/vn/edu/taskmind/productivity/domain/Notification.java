package vn.edu.taskmind.productivity.domain;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Entity
@Table(name = "notifications")
public class Notification {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "NotificationID", nullable = false)
    private Integer notificationId;

    @Column(name = "UserID", nullable = false)
    private Integer userId;

    @Column(name = "ReminderID", nullable = false)
    private Integer reminderId;

    @Column(name = "Title", nullable = false, length = 200)
    private String title;

    @Column(name = "Content", nullable = false, columnDefinition = "text")
    private String content;

    @Column(name = "IsRead", nullable = false, columnDefinition = "tinyint(1)")
    private Boolean isRead = false;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
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
    public Reminder getReminder() { return reminder; }
}
