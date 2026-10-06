package vn.edu.taskmanager;

import vn.edu.taskmanager.data.Task;

/** Ngày ISO so sánh được theo thứ tự thời gian; quá hạn tính theo ngày thực tế. */
public final class TaskSchedule {
    private TaskSchedule() { }
    // Giữ được ngày cũ khi chỉ sửa tên; ngày mới phải từ hôm nay trở đi.
    public static boolean canSaveDate(String date, String originalDate, String today) {
        return date.isEmpty() || date.equals(originalDate) || date.compareTo(today) >= 0;
    }
    public static boolean onDate(Task task, String date) {
        return !task.scheduledDate.isEmpty() && task.scheduledDate.equals(date);
    }
    public static boolean overdue(Task task, String today) {
        return !task.completed && !task.scheduledDate.isEmpty()
                && task.scheduledDate.compareTo(today) < 0;
    }
}
