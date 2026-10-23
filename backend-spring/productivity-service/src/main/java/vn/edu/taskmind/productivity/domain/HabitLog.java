package vn.edu.taskmind.productivity.domain;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

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

    @Column(name = "Status", nullable = false, length = 20)
    private String status;

    @Column(name = "ActualCount", nullable = false)
    private Integer actualCount = 0;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "HabitID", insertable = false, updatable = false)
    private Habit habit;

    public HabitLog() { }

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
    public Habit getHabit() { return habit; }
}
