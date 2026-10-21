package vn.edu.taskmanager.data;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.UUID;
import static org.junit.Assert.*;

/** Mọi dữ liệu thử nằm trong database/Preferences riêng, không đụng dữ liệu người dùng. */
@RunWith(AndroidJUnit4.class)
public class TaskListDatabaseTest {
    private TaskList list(String id, String title) {
        TaskList list = new TaskList(); list.id = id; list.title = title;
        return list;
    }

    @Test public void movingTaskPersistsAndUpdatesBothListsWithoutCopyingTask() {
        Context context = ApplicationProvider.getApplicationContext();
        String name = "lists-test-" + UUID.randomUUID() + ".db";
        TaskDatabase db = Room.databaseBuilder(context, TaskDatabase.class, name).build();
        try {
            long taskId = db.taskDao().insert(new Task("Học Java"));
            db.taskListDao().insert(list("a", "Học tập"));
            db.taskListDao().insert(list("b", "Trong tuần"));
            db.taskListDao().addMember(new TaskListMember("a", taskId));
            db.taskListDao().addMember(new TaskListMember("a", taskId));
            db.taskListDao().addMember(new TaskListMember("b", taskId));
            db.taskDao().setCompleted(taskId, true);
            db.taskListDao().rename("a", "Bài tập");
            db.close();
            db = Room.databaseBuilder(context, TaskDatabase.class, name).build();
            assertEquals("Bài tập", db.taskListDao().find("a").title);
            for (TaskListWithTasks row : db.taskListDao().allLists()) {
                int expected = row.list.id.equals("b") ? 1 : 0;
                assertEquals(expected, row.tasks.size()); assertEquals(expected, row.completedCount());
            }
            assertEquals(1, db.taskDao().allTasks().size());
            db.taskListDao().delete("a");
            assertNotNull(db.taskDao().findById(taskId));
            assertEquals(1, db.taskListDao().allLists().get(0).tasks.size());
            db.taskDao().setCompleted(taskId, false);
            assertEquals(0, db.taskListDao().allLists().get(0).completedCount());
            db.taskDao().editDetails(taskId, "Ôn Java", "", 2, 50, "Một nửa");
            assertEquals("Ôn Java", db.taskListDao().allLists().get(0).tasks.get(0).title);
            db.taskDao().delete(taskId);
            assertTrue(db.taskListDao().allLists().get(0).tasks.isEmpty());
        } finally { db.close(); context.deleteDatabase(name); }
    }

    @Test public void invalidDestinationRollsBackAndUnassigningKeepsTask() {
        Context context = ApplicationProvider.getApplicationContext();
        TaskDatabase db = Room.inMemoryDatabaseBuilder(context, TaskDatabase.class).build();
        try {
            long taskId = db.taskDao().insert(new Task("Keep task"));
            db.taskListDao().insert(list("a", "A"));
            db.taskListDao().addMember(new TaskListMember("a", taskId));
            try {
                db.taskListDao().addMember(new TaskListMember("missing", taskId));
                fail("Invalid destination must fail");
            } catch (android.database.sqlite.SQLiteConstraintException expected) { }
            assertEquals(taskId, db.taskListDao().allLists().get(0).tasks.get(0).id);
            db.taskListDao().clearMembers("a");
            assertTrue(db.taskListDao().allLists().get(0).tasks.isEmpty());
            assertNotNull(db.taskDao().findById(taskId));
        } finally { db.close(); }
    }

    @Test public void versionFiveDuplicatesKeepNewestListAndTaskDetails() {
        Context context = ApplicationProvider.getApplicationContext();
        String name = "lists-v5-" + UUID.randomUUID() + ".db";
        android.database.sqlite.SQLiteDatabase old = context.openOrCreateDatabase(name, 0, null);
        old.execSQL("CREATE TABLE tasks (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, title TEXT NOT NULL, completed INTEGER NOT NULL, progress INTEGER NOT NULL DEFAULT 0, note TEXT NOT NULL DEFAULT '', scheduledDate TEXT NOT NULL DEFAULT '', createdAt INTEGER NOT NULL DEFAULT 0, priority INTEGER NOT NULL DEFAULT 2)");
        old.execSQL("INSERT INTO tasks VALUES (27, 'Keep task', 1, 100, 'Keep note', '2026-10-01', 123, 1)");
        old.execSQL("CREATE TABLE task_lists (id TEXT NOT NULL PRIMARY KEY, title TEXT NOT NULL, createdAt INTEGER NOT NULL)");
        old.execSQL("CREATE TABLE task_list_members (listId TEXT NOT NULL, taskId INTEGER NOT NULL, PRIMARY KEY(listId, taskId), FOREIGN KEY(listId) REFERENCES task_lists(id) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(taskId) REFERENCES tasks(id) ON UPDATE NO ACTION ON DELETE CASCADE)");
        old.execSQL("CREATE INDEX index_task_list_members_taskId ON task_list_members(taskId)");
        old.execSQL("CREATE TABLE task_list_import_state (id TEXT NOT NULL PRIMARY KEY)");
        old.execSQL("INSERT INTO task_list_import_state VALUES ('legacy_preferences_v1')");
        old.execSQL("INSERT INTO task_lists VALUES ('a', 'A', 10), ('b', 'B', 20), ('c', 'C', 20)");
        old.execSQL("INSERT INTO task_list_members VALUES ('a', 27), ('b', 27), ('c', 27)");
        old.setVersion(5); old.close();
        TaskDatabase db = Room.databaseBuilder(context, TaskDatabase.class, name)
                .addMigrations(TaskDatabase.MIGRATION_5_6).build();
        try {
            assertEquals(3, db.taskListDao().allLists().size());
            for (TaskListWithTasks row : db.taskListDao().allLists())
                assertEquals(row.list.id.equals("c") ? 1 : 0, row.tasks.size());
            assertEquals(1, db.taskListDao().imported());
            Task task = db.taskDao().findById(27);
            assertTrue(task.completed); assertEquals(100, task.progress);
            assertEquals("Keep note", task.note); assertEquals("2026-10-01", task.scheduledDate);
            db.taskListDao().addMember(new TaskListMember("a", 27));
            for (TaskListWithTasks row : db.taskListDao().allLists())
                assertEquals(row.list.id.equals("a") ? 1 : 0, row.tasks.size());
        } finally { db.close(); context.deleteDatabase(name); }
    }

    @Test public void versionFourMigratesWithoutChangingTasks() {
        Context context = ApplicationProvider.getApplicationContext();
        String name = "lists-migration-" + UUID.randomUUID() + ".db";
        android.database.sqlite.SQLiteDatabase old = context.openOrCreateDatabase(name, 0, null);
        old.execSQL("CREATE TABLE tasks (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, title TEXT NOT NULL, completed INTEGER NOT NULL, progress INTEGER NOT NULL DEFAULT 0, note TEXT NOT NULL DEFAULT '', scheduledDate TEXT NOT NULL DEFAULT '', createdAt INTEGER NOT NULL DEFAULT 0, priority INTEGER NOT NULL DEFAULT 2)");
        old.execSQL("INSERT INTO tasks VALUES (27, 'Giữ dữ liệu', 0, 50, 'Ghi chú cũ', '2026-10-01', 123, 1)");
        old.setVersion(4); old.close();
        TaskDatabase db = Room.databaseBuilder(context, TaskDatabase.class, name)
                .addMigrations(TaskDatabase.MIGRATION_4_5, TaskDatabase.MIGRATION_5_6).build();
        try {
            Task task = db.taskDao().findById(27);
            assertEquals("Giữ dữ liệu", task.title); assertEquals(50, task.progress);
            assertEquals("Ghi chú cũ", task.note); assertEquals("2026-10-01", task.scheduledDate);
            assertEquals(123, task.createdAt); assertEquals(1, task.priority); assertFalse(task.completed);
            db.taskListDao().insert(list("a", "Danh sách mới"));
            db.taskListDao().addMember(new TaskListMember("a", 27));
            assertEquals(1, db.taskListDao().allLists().get(0).tasks.size());
        } finally { db.close(); context.deleteDatabase(name); }
    }

    @Test public void legacyImportIsAtomicAndDoesNotResurrectDeletedLists() {
        Context base = ApplicationProvider.getApplicationContext();
        String prefix = "lists-legacy-" + UUID.randomUUID();
        Context isolated = new ContextWrapper(base) {
            @Override public SharedPreferences getSharedPreferences(String name, int mode) {
                return super.getSharedPreferences(prefix + name, mode);
            }
        };
        TaskDatabase db = Room.inMemoryDatabaseBuilder(base, TaskDatabase.class).build();
        SharedPreferences prefs = isolated.getSharedPreferences("personal_workspace", 0);
        try {
            long taskId = db.taskDao().insert(new Task("Công việc cũ"));
            LocalStore store = new LocalStore(isolated);
            LocalStore.Entry entry = new LocalStore.Entry();
            entry.type = "list"; entry.title = "Danh sách cũ"; entry.date = LocalStore.today();
            entry.body = taskId + "," + taskId + ",999999,bad"; store.save(entry);
            LocalStore.Entry habit = new LocalStore.Entry();
            habit.type = "habit"; habit.title = "Giữ thói quen"; habit.date = LocalStore.today(); store.save(habit);
            String original = prefs.getString("entries", "");
            LegacyTaskListImporter.importOnce(isolated, db);
            LegacyTaskListImporter.importOnce(isolated, db);
            assertEquals(1, db.taskListDao().allLists().size());
            assertEquals(entry.id, db.taskListDao().allLists().get(0).list.id);
            assertEquals(1, db.taskListDao().allLists().get(0).tasks.size());
            assertEquals(original, prefs.getString("entries", ""));
            assertNotNull(store.find(habit.id));
            db.taskListDao().delete(entry.id);
            LegacyTaskListImporter.importOnce(isolated, db);
            assertTrue(db.taskListDao().allLists().isEmpty());
            assertNotNull(db.taskDao().findById(taskId));
        } finally { db.close(); prefs.edit().clear().commit(); }
    }

    @Test public void malformedLegacyJsonCanBeRetriedWithoutLosingSource() {
        Context base = ApplicationProvider.getApplicationContext();
        String prefix = "lists-invalid-" + UUID.randomUUID();
        Context isolated = new ContextWrapper(base) {
            @Override public SharedPreferences getSharedPreferences(String name, int mode) {
                return super.getSharedPreferences(prefix + name, mode);
            }
        };
        SharedPreferences prefs = isolated.getSharedPreferences("personal_workspace", 0);
        TaskDatabase db = Room.inMemoryDatabaseBuilder(base, TaskDatabase.class).build();
        try {
            prefs.edit().putString("entries", "invalid json").commit();
            try { LegacyTaskListImporter.importOnce(isolated, db); fail("Must reject invalid source"); }
            catch (IllegalStateException expected) { }
            assertEquals(0, db.taskListDao().imported());
            assertEquals("invalid json", prefs.getString("entries", ""));
            prefs.edit().putString("entries", "[]").commit();
            LegacyTaskListImporter.importOnce(isolated, db);
            assertEquals(1, db.taskListDao().imported());
        } finally { db.close(); prefs.edit().clear().commit(); }
    }
}
