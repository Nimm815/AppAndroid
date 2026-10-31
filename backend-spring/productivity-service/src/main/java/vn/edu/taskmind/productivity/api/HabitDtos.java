package vn.edu.taskmind.productivity.api;

import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.time.LocalTime;
public final class HabitDtos {
 public record Input(@NotBlank @Size(max=200) String name,@Size(max=10000) String description,
  @NotBlank @Size(max=32) String icon,@NotNull @Pattern(regexp="#[0-9a-fA-F]{6}") String color,
  @Min(1) @Max(127) int weekdays,@NotNull LocalDate startDate,LocalDate endDate,
  @Min(1) @Max(10000) Integer targetCount,@Size(max=20) String targetUnit,
  LocalTime reminderTime,Boolean reminderEnabled) {}
 public record CheckIn(@NotNull Boolean completed,@Min(0) @Max(10000) Integer actualCount,@Size(max=10000) String note) {}
 public record View(int id,String name,String description,String icon,String color,int weekdays,LocalDate startDate,
  boolean scheduleConfigured,boolean completedToday,int actualCountToday,int currentStreak,int longestStreak,int completedDays,int scheduledDays,double completionRate,
  int targetCount,String targetUnit,LocalTime reminderTime,boolean reminderEnabled) {}
 public record Day(LocalDate date,boolean scheduled,boolean completed,int actualCount) {}
}
