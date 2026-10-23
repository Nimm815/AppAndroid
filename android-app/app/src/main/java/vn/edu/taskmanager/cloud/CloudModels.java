package vn.edu.taskmanager.cloud;

public final class CloudModels {
    public static class TaskList {
        public int id, total, completed;
        public String name, color;
    }
    public static class Task {
        public int id, progress;
        public Integer listId;
        public String title, description, notes, startDate, dueDate, priority, status;
    }
    public static class Habit {
        public int id, weekdays, currentStreak, longestStreak, completedDays, scheduledDays;
        public double completionRate;
        public boolean completedToday, scheduleConfigured;
        public String name, description, icon, color, startDate;
    }
    public static class Day {
        public String date;
        public boolean scheduled, completed;
        public int actualCount;
    }
}
