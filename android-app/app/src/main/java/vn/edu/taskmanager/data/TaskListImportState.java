package vn.edu.taskmanager.data;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/** Đánh dấu nhập dữ liệu trong cùng transaction với danh sách, tránh nhập lại sau khi xóa. */
@Entity(tableName = "task_list_import_state")
public class TaskListImportState {
    @PrimaryKey @NonNull public String id = "legacy_preferences_v1";
}
