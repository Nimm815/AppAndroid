package vn.edu.taskmanager;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;
import vn.edu.taskmanager.data.LocalStore;
import vn.edu.taskmanager.data.DailyHistory;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class ActivityHistoryTest {
    @Test public void allPlannedTasksAppearAfterRolloverAndYesterdayDoesNotChange() {
        vn.edu.taskmanager.data.Task pending = new vn.edu.taskmanager.data.Task("Chưa làm");
        pending.id = 1; pending.scheduledDate = "2026-10-09";
        vn.edu.taskmanager.data.Task partial = new vn.edu.taskmanager.data.Task("Học Java");
        partial.id = 2; partial.scheduledDate = "2026-10-09"; partial.progress = 50;
        vn.edu.taskmanager.data.Task done = new vn.edu.taskmanager.data.Task("Nộp bài");
        done.id = 3; done.scheduledDate = "2026-10-09"; done.progress = 100; done.completed = true;
        java.util.List<vn.edu.taskmanager.data.Task> tasks = java.util.Arrays.asList(pending, partial, done);
        java.util.List<DailyHistory.Row> rows = DailyHistory.reconcile(java.util.Collections.emptyList(), tasks,
                java.util.Collections.emptyList(), "2026-10-09");
        assertEquals(3, rows.size());
        partial.completed = true; partial.progress = 100; partial.title = "Đã sửa ngày sau";
        pending.completed = true;
        rows = DailyHistory.reconcile(rows, tasks, java.util.Collections.emptyList(), "2026-10-10");
        int total = 0;
        for (DailyHistory.Row row : rows) {
            assertEquals("2026-10-09", row.date); total += row.progress;
            if (row.sourceId.equals("1")) assertEquals(0, row.progress);
            if (row.sourceId.equals("2")) { assertEquals(50, row.progress); assertEquals("Học Java", row.title); }
            if (row.sourceId.equals("3")) assertEquals(100, row.progress);
        }
        assertEquals(50, total / rows.size());
        assertEquals(3, DailyHistory.reconcile(rows, java.util.Collections.emptyList(),
                java.util.Collections.emptyList(), "2026-10-11").size());
    }

    @Test public void partialHabitProgressAndSnapshotsSurviveReopening() {
        Context app = ApplicationProvider.getApplicationContext();
        String name = "history-test-" + java.util.UUID.randomUUID();
        Context context = new ContextWrapper(app) {
            @Override public SharedPreferences getSharedPreferences(String requested, int mode) {
                return super.getSharedPreferences(name + requested, mode);
            }
        };
        try {
            LocalStore store = new LocalStore(context);
            LocalStore.Entry habit = new LocalStore.Entry();
            habit.type = "habit"; habit.title = "Đọc sách"; habit.date = "2026-10-09";
            habit.body = ""; habit.time = "09:00"; store.save(habit);
            habit.created = "2026-10-09"; store.save(habit);
            store.setHabitProgress(habit, "2026-10-09", 50);
            assertEquals(Integer.valueOf(50), new LocalStore(context).find(habit.id).dailyProgress.get("2026-10-09"));
            DailyHistory history = new DailyHistory(context);
            history.sync(java.util.Collections.emptyList(), store.entries("habit"), "2026-10-09");
            store.setHabitProgress(habit, "2026-10-10", 100);
            history.sync(java.util.Collections.emptyList(), store.entries("habit"), "2026-10-10");
            DailyHistory reopened = new DailyHistory(context);
            assertEquals(2, reopened.rows().size());
            assertEquals(100, reopened.rows().get(0).progress);
            assertEquals(50, reopened.rows().get(1).progress);
            store.delete(habit.id);
            history.sync(java.util.Collections.emptyList(), store.entries("habit"), "2026-10-11");
            assertEquals(2, history.rows().size());
        } finally {
            app.getSharedPreferences(name + "personal_workspace", 0).edit().clear().commit();
            app.getSharedPreferences(name + "daily_activity_history", 0).edit().clear().commit();
        }
    }
}
