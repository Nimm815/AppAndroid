package vn.edu.taskmanager;

import vn.edu.taskmanager.data.Task;
import vn.edu.taskmanager.util.VietnameseUtils;

public final class TaskFilters {
    public static final int DATE_CURRENT_VIEW = 0;
    public static final int DATE_ALL = 1;
    public static final int DATE_OVERDUE = 2;
    public static final int DATE_UNSCHEDULED = 3;

    private TaskFilters() { }

    public static boolean matches(Task task, String query, int status, int priority) {
        return matchesSearch(task, query) && matchesStatus(task, status)
                && matchesPriority(task, priority);
    }

    public static boolean matchesSearch(Task task, String query) {
        String keyword = VietnameseUtils.stripDiacritics(query);
        return keyword.isEmpty()
                || VietnameseUtils.containsIgnoreAccents(task.title, keyword)
                || VietnameseUtils.containsIgnoreAccents(task.note, keyword);
    }

    public static boolean matchesStatus(Task task, int status) {
        return status == 0 || status == 1 && !task.completed || status == 2 && task.completed;
    }

    public static boolean matchesPriority(Task task, int priority) {
        return priority == 0 || task.priority == priority;
    }

    public static boolean isOverdue(Task task, String today) {
        return TaskSchedule.overdue(task, today);
    }

    public static boolean isUnscheduled(Task task) {
        return task.scheduledDate.isEmpty();
    }
}
