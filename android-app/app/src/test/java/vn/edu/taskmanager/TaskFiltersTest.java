package vn.edu.taskmanager;

import org.junit.Test;
import vn.edu.taskmanager.data.Task;

import static org.junit.Assert.*;

public class TaskFiltersTest {
    @Test public void searchMatchesTitleAndNoteWithoutCaseOrVietnameseAccentSensitivity() {
        Task task = new Task("Đọc sách mỗi tối");
        task.note = "Ghi lại trang đã đọc";

        assertTrue(TaskFilters.matchesSearch(task, "doc sach"));
        assertTrue(TaskFilters.matchesSearch(task, "SÁCH"));
        assertTrue(TaskFilters.matchesSearch(task, "trang da doc"));
        assertFalse(TaskFilters.matchesSearch(task, "chạy bộ"));
        assertTrue(TaskFilters.matchesSearch(task, "  "));
    }

    @Test public void statusAndPriorityFiltersCanBeCombined() {
        Task task = new Task("Ôn bài");
        task.priority = 3;

        assertTrue(TaskFilters.matchesStatus(task, 1));
        assertFalse(TaskFilters.matchesStatus(task, 2));
        assertTrue(TaskFilters.matchesPriority(task, 0));
        assertTrue(TaskFilters.matchesPriority(task, 3));
        assertFalse(TaskFilters.matchesPriority(task, 2));
        assertTrue(TaskFilters.matches(task, "on bai", 1, 3));
        assertFalse(TaskFilters.matches(task, "on bai", 1, 2));
        assertFalse(TaskFilters.matches(task, "doc sach", 1, 3));

        task.completed = true;
        assertTrue(TaskFilters.matchesStatus(task, 2));
        assertFalse(TaskFilters.matchesStatus(task, 1));
        assertTrue(TaskFilters.matches(task, "ôn bài", 2, 3));
    }

    @Test public void dateFiltersMatchOverdueAndUnscheduledTasks() {
        Task overdue = new Task("Việc quá hạn");
        overdue.scheduledDate = "2026-10-23";
        Task unscheduled = new Task("Việc chưa lên lịch");

        assertTrue(TaskFilters.isOverdue(overdue, "2026-10-24"));
        assertFalse(TaskFilters.isOverdue(overdue, "2026-10-23"));
        assertFalse(TaskFilters.isOverdue(new Task("Đã xong"), "2026-10-24"));
        assertTrue(TaskFilters.isUnscheduled(unscheduled));
        assertFalse(TaskFilters.isUnscheduled(overdue));
    }
}
