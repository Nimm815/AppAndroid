package vn.edu.taskmind.productivity.domain;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

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

    @Column(name = "RemindAt", nullable = false, columnDefinition = "datetime")
    private LocalDateTime remindAt;

    @Column(name = "IsEnabled", nullable = false, columnDefinition = "tinyint(1)")
    private Boolean isEnabled = true;

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
    public LocalDateTime getRemindAt() { return remindAt; }
    public void setRemindAt(LocalDateTime remindAt) { this.remindAt = remindAt; }
    public Boolean getIsEnabled() { return isEnabled; }
    public void setIsEnabled(Boolean isEnabled) { this.isEnabled = isEnabled; }
    public CalendarEvent getEvent() { return event; }
}
