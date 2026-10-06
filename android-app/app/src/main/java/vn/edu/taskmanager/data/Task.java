package vn.edu.taskmanager.data;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;

@Entity(tableName = "tasks")
public class Task {
    @PrimaryKey(autoGenerate = true)
    public long id;
    @NonNull
    public String title = "";
    public boolean completed;
    // ISO yyyy-MM-dd; rỗng nghĩa là chưa lên lịch (bao gồm dữ liệu cũ).
    @NonNull @ColumnInfo(defaultValue = "''")
    public String scheduledDate = "";
    @ColumnInfo(defaultValue = "0")
    public long createdAt = System.currentTimeMillis();
    // 1: Làm ngay, 2: Lên lịch, 3: Ủy quyền, 4: Loại bỏ.
    @ColumnInfo(defaultValue = "2")
    public int priority = 2;

    public Task(@NonNull String title) {
        this.title = title;
    }
}
