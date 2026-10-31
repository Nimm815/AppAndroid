package vn.edu.taskmind.productivity.domain;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/** Maps only columns in the existing productivity_db.habits table. */
@Entity
@Table(name = "habits")
public class Habit {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "HabitID", nullable = false)
    private Integer habitId;
    @Column(name = "UserID", nullable = false)
    private Integer userId;
    @Column(name = "HabitName", nullable = false, length = 200)
    private String habitName;
    @Column(name = "Description", columnDefinition = "text")
    private String description;
    @Column(name = "Frequency", nullable = false, length = 20, columnDefinition = "enum('DAILY','WEEKLY','CUSTOM')")
    private String frequency;
    @Column(name = "StartDate", nullable = false)
    private LocalDate startDate;
    @Column(name = "EndDate")
    private LocalDate endDate;
    @Column(name = "TargetCount", nullable = false)
    private Integer targetCount = 1;
    @Column(name = "IsActive", nullable = false, columnDefinition = "tinyint(1)")
    private Boolean isActive = true;
    @Column(name = "CreatedAt", nullable = false, columnDefinition = "datetime")
    private LocalDateTime createdAt;
    @Column(name = "UpdatedAt", nullable = false, columnDefinition = "datetime")
    private LocalDateTime updatedAt;

    @PrePersist
    private void initializeTimestamps() {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
    }

    @PreUpdate
    private void updateTimestamp() { updatedAt = LocalDateTime.now(ZoneOffset.UTC); }

    public Integer getHabitId() { return habitId; }
    public void setHabitId(Integer value) { habitId = value; }
    public Integer getUserId() { return userId; }
    public void setUserId(Integer value) { userId = value; }
    public String getHabitName() { return habitName; }
    public void setHabitName(String value) { habitName = value; }
    public String getDescription() { return description; }
    public void setDescription(String value) { description = value; }
    public String getFrequency() { return frequency; }
    public void setFrequency(String value) { frequency = value; }
    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate value) { startDate = value; }
    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate value) { endDate = value; }
    public Integer getTargetCount() { return targetCount; }
    public void setTargetCount(Integer value) { targetCount = value; }
    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean value) { isActive = value; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
