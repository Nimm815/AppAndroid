package vn.edu.taskmanager;

import android.graphics.Color;

/** Bốn nhóm quan trọng/khẩn cấp; chỉ phân loại, không tự xóa hay giao việc. */
public final class TaskPriority {
    private TaskPriority() { }
    public static final String[] LABELS = {"Làm ngay", "Lên lịch", "Ủy quyền", "Loại bỏ"};
    public static final String[] DETAILS = {"Quan trọng · Khẩn cấp", "Quan trọng · Không khẩn cấp",
            "Không quan trọng · Khẩn cấp", "Không quan trọng · Không khẩn cấp"};
    public static final int[] COLORS = {Color.rgb(0, 185, 132), Color.rgb(226, 183, 0),
            Color.rgb(0, 139, 161), Color.rgb(242, 74, 23)};
    public static int index(int priority) { return priority >= 1 && priority <= 4 ? priority - 1 : 1; }
    public static String label(int priority) { return LABELS[index(priority)]; }
    public static int color(int priority) { return COLORS[index(priority)]; }
}
