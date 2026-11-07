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
    @ColumnInfo(defaultValue = "0")
    public int progress;
    @NonNull @ColumnInfo(defaultValue = "''")
    public String note = "";
    // ISO yyyy-MM-dd; rỗng nghĩa là chưa lên lịch (bao gồm dữ liệu cũ).
    @NonNull @ColumnInfo(defaultValue = "''")
    public String scheduledDate = "";
    @ColumnInfo(defaultValue = "0")
    public long createdAt = System.currentTimeMillis();
    // 1: Làm ngay, 2: Lên lịch, 3: Ủy quyền, 4: Loại bỏ.
    @ColumnInfo(defaultValue = "2")
    public int priority = 2;
    // Liên kết nội bộ tới sự kiện Lịch để đồng bộ chỉnh sửa/xóa hai chiều.
    @NonNull @ColumnInfo(defaultValue = "''")
    public String calendarEventId = "";
    @NonNull @ColumnInfo(defaultValue = "''")
    public String scheduledTime = "";

    public Task(@NonNull String title) {
        this.title = title;
    }
}
