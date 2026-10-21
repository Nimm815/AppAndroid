package vn.edu.taskmanager.data;

import android.content.Context;
import java.util.List;

/** Chỉ nhập danh sách; giữ nguyên JSON cũ và các loại dữ liệu khác. Chạy trên luồng nền. */
public final class LegacyTaskListImporter {
    private LegacyTaskListImporter() { }

    public static void importOnce(Context context, TaskDatabase db) {
        db.runInTransaction(() -> {
            TaskListDao lists = db.taskListDao();
            if (lists.imported() != 0) return;
            List<LocalStore.Entry> legacy = new LocalStore(context).entries("list");
            for (LocalStore.Entry entry : legacy) {
                TaskList list = new TaskList();
                list.id = entry.id; list.title = entry.title;
                // Không suy đoán thời điểm tạo từ dữ liệu cũ.
                list.createdAt = 0;
                lists.insert(list);
                if (entry.body == null || entry.body.isEmpty()) continue;
                for (String member : entry.body.split(",")) {
                    try {
                        long id = Long.parseLong(member.trim());
                        if (db.taskDao().findById(id) != null)
                            lists.addMember(new TaskListMember(list.id, id));
                    } catch (NumberFormatException ignored) {
                        // Bỏ liên kết không hợp lệ, vẫn giữ danh sách và nguồn JSON.
                    }
                }
            }
            lists.markImported(new TaskListImportState());
        });
    }
}
