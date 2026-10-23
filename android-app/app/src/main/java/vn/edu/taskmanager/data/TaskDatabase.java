package vn.edu.taskmanager.data;

import android.content.Context;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

@Database(entities = {Task.class, TaskList.class, TaskListMember.class, TaskListImportState.class}, version = 6, exportSchema = true)
public abstract class TaskDatabase extends RoomDatabase {
    public abstract TaskDao taskDao();
    public abstract TaskListDao taskListDao();
    public static final androidx.room.migration.Migration MIGRATION_1_2 =
            new androidx.room.migration.Migration(1, 2) {
                @Override public void migrate(@androidx.annotation.NonNull androidx.sqlite.db.SupportSQLiteDatabase db) {
                    db.execSQL("ALTER TABLE tasks ADD COLUMN scheduledDate TEXT NOT NULL DEFAULT ''");
                    db.execSQL("ALTER TABLE tasks ADD COLUMN createdAt INTEGER NOT NULL DEFAULT 0");
                }
            };

    public static final androidx.room.migration.Migration MIGRATION_2_3 =
            new androidx.room.migration.Migration(2, 3) {
                @Override public void migrate(@androidx.annotation.NonNull androidx.sqlite.db.SupportSQLiteDatabase db) {
                    db.execSQL("ALTER TABLE tasks ADD COLUMN priority INTEGER NOT NULL DEFAULT 2");
                }
            };

    public static final androidx.room.migration.Migration MIGRATION_3_4 =
            new androidx.room.migration.Migration(3, 4) {
                @Override public void migrate(@androidx.annotation.NonNull androidx.sqlite.db.SupportSQLiteDatabase db) {
                    db.execSQL("ALTER TABLE tasks ADD COLUMN progress INTEGER NOT NULL DEFAULT 0");
                    db.execSQL("ALTER TABLE tasks ADD COLUMN note TEXT NOT NULL DEFAULT ''");
                    db.execSQL("UPDATE tasks SET progress = 100 WHERE completed = 1");
                }
            };

    public static final androidx.room.migration.Migration MIGRATION_4_5 =
            new androidx.room.migration.Migration(4, 5) {
                @Override public void migrate(@androidx.annotation.NonNull androidx.sqlite.db.SupportSQLiteDatabase db) {
                    db.execSQL("CREATE TABLE IF NOT EXISTS task_lists (id TEXT NOT NULL PRIMARY KEY, title TEXT NOT NULL, createdAt INTEGER NOT NULL)");
                    db.execSQL("CREATE TABLE IF NOT EXISTS task_list_members (listId TEXT NOT NULL, taskId INTEGER NOT NULL, PRIMARY KEY(listId, taskId), FOREIGN KEY(listId) REFERENCES task_lists(id) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(taskId) REFERENCES tasks(id) ON UPDATE NO ACTION ON DELETE CASCADE)");
                    db.execSQL("CREATE INDEX IF NOT EXISTS index_task_list_members_taskId ON task_list_members(taskId)");
                    db.execSQL("CREATE TABLE IF NOT EXISTS task_list_import_state (id TEXT NOT NULL PRIMARY KEY)");
                }
            };

    public static final androidx.room.migration.Migration MIGRATION_5_6 =
            new androidx.room.migration.Migration(5, 6) {
                @Override public void migrate(@androidx.annotation.NonNull androidx.sqlite.db.SupportSQLiteDatabase db) {
                    // Bản cũ có thể chứa một việc ở nhiều danh sách: giữ danh sách tạo mới nhất.
                    // Nếu cùng thời điểm tạo, dùng ID giảm dần để có kết quả xác định.
                    db.execSQL("DELETE FROM task_list_members WHERE listId != (SELECT m.listId FROM task_list_members m JOIN task_lists l ON l.id = m.listId WHERE m.taskId = task_list_members.taskId ORDER BY l.createdAt DESC, l.id DESC LIMIT 1)");
                    db.execSQL("DROP INDEX IF EXISTS index_task_list_members_taskId");
                    db.execSQL("CREATE UNIQUE INDEX index_task_list_members_taskId ON task_list_members(taskId)");
                }
            };

    private static final java.util.Map<String,TaskDatabase> databases = new java.util.HashMap<>();
    public static synchronized TaskDatabase getInstance(Context context) {
        String name=vn.edu.taskmanager.auth.Session.storage(context,"personal_tasks.db");
        TaskDatabase database=databases.get(name);
        if(database==null){
            database=Room.databaseBuilder(context.getApplicationContext(),TaskDatabase.class,name)
                .addMigrations(MIGRATION_1_2,MIGRATION_2_3,MIGRATION_3_4,MIGRATION_4_5,MIGRATION_5_6).build();
            databases.put(name,database);
        }
        return database;
    }
}
