package vn.edu.taskmind.productivity.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "reminders")
public class Reminder {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ReminderID", nullable = false)
    private Integer reminderId;

    @Column(name = "UserID", nullable = false)
    private Integer userId;

    @Column(name = "TaskID", nullable = true)
    private Integer taskId;

    @Column(name = "EventID", nullable = true)
    private Integer eventId;

    @Column(name = "Title", nullable = false, length = 255)
    private String title;

    @Column(name = "RemindAt", nullable = false, columnDefinition = "datetime")
    private LocalDateTime remindAt;

    @Column(name = "IsEnabled", nullable = false, columnDefinition = "tinyint(1)")
    private Boolean isEnabled = true;

    @Column(name = "RepeatRule", length = 100)
    private String repeatRule;

    @Column(name = "CreatedAt", nullable = false, columnDefinition = "datetime")
    private LocalDateTime createdAt;

    @Column(name = "UpdatedAt", nullable = false, columnDefinition = "datetime")
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
    }

    @PreUpdate
    void onUpdate() { updatedAt = LocalDateTime.now(); }

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "EventID", insertable = false, updatable = false)
    private CalendarEvent event;

    public Reminder() { }

    public Integer getReminderId() { return reminderId; }
    public void setReminderId(Integer reminderId) { this.reminderId = reminderId; }
    public Integer getUserId() { return userId; }
    public void setUserId(Integer userId) { this.userId = userId; }
    public Integer getTaskId() { return taskId; }
    public void setTaskId(Integer taskId) { this.taskId = taskId; }
    public Integer getEventId() { return eventId; }
    public void setEventId(Integer eventId) { this.eventId = eventId; }
    public String getTitle() { return title; }
    public void setTitle(String value) { title = value; }
    public LocalDateTime getRemindAt() { return remindAt; }
    public void setRemindAt(LocalDateTime remindAt) { this.remindAt = remindAt; }
    public Boolean getIsEnabled() { return isEnabled; }
    public void setIsEnabled(Boolean isEnabled) { this.isEnabled = isEnabled; }
    public String getRepeatRule() { return repeatRule; }
    public void setRepeatRule(String value) { repeatRule = value; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public CalendarEvent getEvent() { return event; }
}
