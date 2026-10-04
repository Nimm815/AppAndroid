package vn.edu.taskmanager.data;

import android.content.Context;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

@Database(entities = {Task.class}, version = 1, exportSchema = true)
public abstract class TaskDatabase extends RoomDatabase {
    public abstract TaskDao taskDao();
    private static volatile TaskDatabase instance;

    public static TaskDatabase getInstance(Context context) {
        if (instance == null) {
            synchronized (TaskDatabase.class) {
                if (instance == null) {
                    instance = Room.databaseBuilder(context.getApplicationContext(),
                            TaskDatabase.class, "personal_tasks.db").build();
                }
            }
        }
        return instance;
    }
}
