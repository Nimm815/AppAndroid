package vn.edu.taskmind.task.domain;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Entity
@Table(name = "tasks")
public class Task {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "TaskID", nullable = false)
    private Integer taskId;

    @Column(name = "UserID", nullable = false)
    private Integer userId;

    @Column(name = "Title", nullable = false, length = 200)
    private String title;

    @Column(name = "Priority", nullable = false, length = 20)
    private String priority;

    @Column(name = "Status", nullable = false, length = 20)
    private String status;

    @Column(name = "Progress", nullable = false)
    private Byte progress = 0;

    @Column(name = "DueDate", nullable = true, columnDefinition = "datetime")
    private LocalDateTime dueDate;

    @Column(name = "CompletedAt", nullable = true, columnDefinition = "datetime")
    private LocalDateTime completedAt;

    public Task() { }

    public Integer getTaskId() { return taskId; }
    public void setTaskId(Integer taskId) { this.taskId = taskId; }
    public Integer getUserId() { return userId; }
    public void setUserId(Integer userId) { this.userId = userId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Byte getProgress() { return progress; }
    public void setProgress(Byte progress) { this.progress = progress; }
    public LocalDateTime getDueDate() { return dueDate; }
    public void setDueDate(LocalDateTime dueDate) { this.dueDate = dueDate; }
    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }
    @Column(name="Description", columnDefinition="text")
    private String description;
    public String getDescription() { return description; }
    public void setDescription(String value) { description=value; }
    @Column(name="Notes", columnDefinition="text")
    private String notes;
    public String getNotes() { return notes; }
    public void setNotes(String value) { notes=value; }
    @Column(name="StartDate", columnDefinition="datetime")
    private LocalDateTime startDate;
    public LocalDateTime getStartDate() { return startDate; }
    public void setStartDate(LocalDateTime value) { startDate=value; }
}
