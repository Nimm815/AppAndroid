package vn.edu.taskmanager.data;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.annotation.NonNull;

@Entity(tableName = "tasks")
public class Task {
    @PrimaryKey(autoGenerate = true)
    public long id;
    @NonNull
    public String title = "";
    public boolean completed;

    public Task(@NonNull String title) {
        this.title = title;
    }
}
