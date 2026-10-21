package vn.edu.taskmanager.data;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "task_lists")
public class TaskList {
    @PrimaryKey @NonNull public String id = "";
    @NonNull public String title = "";
    public long createdAt;
}
