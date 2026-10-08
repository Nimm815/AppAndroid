package vn.edu.taskmanager.data;

import android.content.Context;
import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class TaskDatabaseTest {
    @Test public void priorityMigrationKeepsVersionTwoTasksAndPersistsAllFourGroups() {
        Context context = ApplicationProvider.getApplicationContext();
        String name = "task-priority-migration-test.db";
        context.deleteDatabase(name);
        android.database.sqlite.SQLiteDatabase old = context.openOrCreateDatabase(name, 0, null);
        old.execSQL("CREATE TABLE tasks (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, title TEXT NOT NULL, completed INTEGER NOT NULL, scheduledDate TEXT NOT NULL DEFAULT '', createdAt INTEGER NOT NULL DEFAULT 0)");
        old.execSQL("INSERT INTO tasks VALUES (23,'Giữ công việc cũ',1,'2026-10-05',1234)");
        old.setVersion(2); old.close();
        TaskDatabase db = Room.databaseBuilder(context, TaskDatabase.class, name)
                .addMigrations(TaskDatabase.MIGRATION_2_3, TaskDatabase.MIGRATION_3_4).build();
        try {
            Task task = db.taskDao().findById(23);
            assertEquals("Giữ công việc cũ", task.title); assertTrue(task.completed);
            assertEquals("2026-10-05", task.scheduledDate); assertEquals(1234, task.createdAt);
            assertEquals(2, task.priority); assertEquals(100, task.progress); assertEquals("", task.note);
            for (int priority = 1; priority <= 4; priority++) {
                db.taskDao().editWithPriority(23, task.title, task.scheduledDate, priority);
                db.close();
                db = Room.databaseBuilder(context, TaskDatabase.class, name).build();
                assertEquals(priority, db.taskDao().findById(23).priority);
                assertTrue(db.taskDao().findById(23).completed);
            }
        } finally { db.close(); context.deleteDatabase(name); }
    }

    @Test public void migrationKeepsOldTasksWithoutInventingDates() {
        Context context = ApplicationProvider.getApplicationContext();
        String name = "task-migration-test.db";
        context.deleteDatabase(name);
        android.database.sqlite.SQLiteDatabase old = context.openOrCreateDatabase(name, 0, null);
        old.execSQL("CREATE TABLE tasks (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, title TEXT NOT NULL, completed INTEGER NOT NULL)");
        old.execSQL("INSERT INTO tasks (id,title,completed) VALUES (17,'Công việc cũ',1)");
        old.setVersion(1); old.close();
        TaskDatabase db = Room.databaseBuilder(context, TaskDatabase.class, name)
                .addMigrations(TaskDatabase.MIGRATION_1_2, TaskDatabase.MIGRATION_2_3, TaskDatabase.MIGRATION_3_4).build();
        try {
            Task task = db.taskDao().findById(17);
            assertEquals("Công việc cũ", task.title); assertTrue(task.completed);
            assertEquals("", task.scheduledDate); assertEquals(0, task.createdAt);
            db.taskDao().edit(17, task.title, "2026-10-08");
            db.close();
            db = Room.databaseBuilder(context, TaskDatabase.class, name).build();
            assertEquals("2026-10-08", db.taskDao().findById(17).scheduledDate);
            assertTrue(db.taskDao().findById(17).completed);
        } finally { db.close(); context.deleteDatabase(name); }
    }

    @Test public void taskDateAndOverdueAreIndependentOfCreationAndViewedDay() {
        Task task = new Task("Nộp bài"); task.scheduledDate = "2026-10-05";
        assertTrue(vn.edu.taskmanager.TaskSchedule.onDate(task, "2026-10-05"));
        assertFalse(vn.edu.taskmanager.TaskSchedule.onDate(task, "2026-10-06"));
        assertFalse(vn.edu.taskmanager.TaskSchedule.overdue(task, "2026-10-05"));
        assertTrue(vn.edu.taskmanager.TaskSchedule.overdue(task, "2026-10-06"));
        task.completed = true;
        assertFalse(vn.edu.taskmanager.TaskSchedule.overdue(task, "2026-10-06"));
        assertTrue(vn.edu.taskmanager.TaskSchedule.onDate(task, "2026-10-05"));
        task.completed = false; task.scheduledDate = "2026-10-08";
        assertFalse(vn.edu.taskmanager.TaskSchedule.overdue(task, "2026-10-06"));
        task.scheduledDate = "";
        assertFalse(vn.edu.taskmanager.TaskSchedule.overdue(task, "2026-10-06"));
        assertFalse(vn.edu.taskmanager.TaskSchedule.onDate(task, "2026-10-06"));
    }

    @Test public void tasksSurviveReopeningAndSupportUpdatesAndDeletion() {
        Context context = ApplicationProvider.getApplicationContext();
        String name = "task-persistence-test.db";
        context.deleteDatabase(name);
        TaskDatabase db = Room.databaseBuilder(context, TaskDatabase.class, name).build();
        try {
            long id = db.taskDao().insert(new Task("Học Android"));
            db.close();
            db = Room.databaseBuilder(context, TaskDatabase.class, name).build();
            assertEquals("Học Android", db.taskDao().findById(id).title);
            assertFalse(db.taskDao().findById(id).completed);
            db.taskDao().editDetails(id, "Ôn Room", "2026-10-09", 2, 50, "Đã đọc một nửa");
            assertEquals(50, db.taskDao().findById(id).progress);
            assertFalse(db.taskDao().findById(id).completed);
            assertEquals("Đã đọc một nửa", db.taskDao().findById(id).note);
            db.taskDao().setCompleted(id, true);
            assertEquals("Ôn Room", db.taskDao().findById(id).title);
            assertTrue(db.taskDao().findById(id).completed);
            db.taskDao().setCompleted(id, false);
            assertFalse(db.taskDao().findById(id).completed);
            db.taskDao().delete(id);
            assertNull(db.taskDao().findById(id));
        } finally {
            db.close();
            context.deleteDatabase(name);
        }
    }
}
