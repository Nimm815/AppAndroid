package vn.edu.taskmind.productivity.domain;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "habitlogs")
public class HabitLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "HabitLogID", nullable = false)
    private Long habitLogId;

    @Column(name = "HabitID", nullable = false)
    private Integer habitId;

    @Column(name = "LogDate", nullable = false)
    private LocalDate logDate;

    @Column(name = "Status", nullable = false, length = 20,
            columnDefinition = "enum('COMPLETED','PARTIAL','MISSED','SKIPPED')")
    private String status;

    @Column(name = "ActualCount", nullable = false)
    private Integer actualCount = 0;

    @Column(name = "Note", columnDefinition = "text")
    private String note;

    @Column(name = "CreatedAt", nullable = false, columnDefinition = "datetime")
    private java.time.LocalDateTime createdAt;

    @Column(name = "UpdatedAt", nullable = false, columnDefinition = "datetime")
    private java.time.LocalDateTime updatedAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "HabitID", insertable = false, updatable = false)
    private Habit habit;

    public HabitLog() { }

    @PrePersist
    private void initializeTimestamps() {
        java.time.LocalDateTime now = java.time.LocalDateTime.now(java.time.ZoneOffset.UTC);
        if (createdAt == null) createdAt = now;
        updatedAt = now;
        if (actualCount == null) actualCount = 0;
    }

    @PreUpdate
    private void updateTimestamp() { updatedAt = java.time.LocalDateTime.now(java.time.ZoneOffset.UTC); }

    public Long getHabitLogId() { return habitLogId; }
    public void setHabitLogId(Long habitLogId) { this.habitLogId = habitLogId; }
    public Integer getHabitId() { return habitId; }
    public void setHabitId(Integer habitId) { this.habitId = habitId; }
    public LocalDate getLogDate() { return logDate; }
    public void setLogDate(LocalDate logDate) { this.logDate = logDate; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getActualCount() { return actualCount; }
    public void setActualCount(Integer actualCount) { this.actualCount = actualCount; }
    public String getNote() { return note; }
    public void setNote(String value) { note = value; }
    public java.time.LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(java.time.LocalDateTime value) { createdAt = value; }
    public java.time.LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(java.time.LocalDateTime value) { updatedAt = value; }
    public Habit getHabit() { return habit; }
}
