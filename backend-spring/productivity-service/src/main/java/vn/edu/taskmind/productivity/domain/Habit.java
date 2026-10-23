package vn.edu.taskmind.productivity.domain;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Entity
@Table(name = "habits")
public class Habit {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "HabitID", nullable = false)
    private Integer habitId;

    @Column(name = "UserID", nullable = false)
    private Integer userId;

    @Column(name = "HabitName", nullable = false, length = 200)
    private String habitName;

    @Column(name = "Frequency", nullable = false, length = 20)
    private String frequency;

    @Column(name = "TargetCount", nullable = false)
    private Integer targetCount = 1;

    @Column(name = "IsActive", nullable = false, columnDefinition = "tinyint(1)")
    private Boolean isActive = true;

    public Habit() { }

    public Integer getHabitId() { return habitId; }
    public void setHabitId(Integer habitId) { this.habitId = habitId; }
    public Integer getUserId() { return userId; }
    public void setUserId(Integer userId) { this.userId = userId; }
    public String getHabitName() { return habitName; }
    public void setHabitName(String habitName) { this.habitName = habitName; }
    public String getFrequency() { return frequency; }
    public void setFrequency(String frequency) { this.frequency = frequency; }
    public Integer getTargetCount() { return targetCount; }
    public void setTargetCount(Integer targetCount) { this.targetCount = targetCount; }
    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }
    @Column(name="Description", columnDefinition="text")
    private String description;
    public String getDescription() { return description; }
    public void setDescription(String value) { description=value; }
    @Column(name="Icon", length=32)
    private String icon;
    public String getIcon() { return icon; }
    public void setIcon(String value) { icon=value; }
    @Column(name="Color", length=7)
    private String color;
    public String getColor() { return color; }
    public void setColor(String value) { color=value; }
    @Column(name="Weekdays")
    private Integer weekdays;
    public Integer getWeekdays() { return weekdays; }
    public void setWeekdays(Integer value) { weekdays=value; }
    @Column(name="StartDate")
    private LocalDate startDate;
    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate value) { startDate=value; }
    @Column(name="TargetUnit", nullable=false, length=20)
    private String targetUnit = "lần";
    public String getTargetUnit() { return targetUnit; }
    public void setTargetUnit(String value) { targetUnit=value; }
    @Column(name="ReminderTime")
    private java.time.LocalTime reminderTime;
    public java.time.LocalTime getReminderTime() { return reminderTime; }
    public void setReminderTime(java.time.LocalTime value) { reminderTime=value; }
    @Column(name="ReminderEnabled", nullable=false, columnDefinition="tinyint(1)")
    private Boolean reminderEnabled = false;
    public Boolean getReminderEnabled() { return reminderEnabled; }
    public void setReminderEnabled(Boolean value) { reminderEnabled=value; }
}
