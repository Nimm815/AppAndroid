package vn.edu.taskmanager.data;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;
import java.util.*;
import java.text.SimpleDateFormat;

/** Mỗi công việc/thói quen có một kết quả cho mỗi ngày; ngày cũ không bị ghi đè. */
public class DailyHistory {
    public static class Row {
        public String key, date, kind, sourceId, title, note = "";
        public int progress;
        public boolean bookmarked;
    }
    private final SharedPreferences preferences;
    public DailyHistory(Context context) {
        preferences = context.getSharedPreferences("daily_activity_history", Context.MODE_PRIVATE);
    }
    public List<Row> rows() {
        List<Row> rows = new ArrayList<>();
        try {
            JSONArray data = new JSONArray(preferences.getString("rows", "[]"));
            for (int i = 0; i < data.length(); i++) {
                JSONObject value = data.getJSONObject(i); Row row = new Row();
                row.key = value.getString("key"); row.date = value.getString("date");
                row.kind = value.getString("kind"); row.sourceId = value.getString("sourceId");
                row.title = value.getString("title"); row.note = value.optString("note");
                row.progress = value.getInt("progress"); row.bookmarked = value.optBoolean("bookmarked");
                rows.add(row);
            }
        } catch (JSONException error) { throw new IllegalStateException("Không đọc được lịch sử hằng ngày", error); }
        return rows;
    }
    public void sync(List<Task> tasks, List<LocalStore.Entry> habits, String today) {
        synchronized (DailyHistory.class) { write(reconcile(rows(), tasks, habits, today)); }
    }
    // Có thể kiểm thử chuyển ngày mà không cần thay đổi đồng hồ điện thoại.
    public static List<Row> reconcile(List<Row> saved, List<Task> tasks,
                                      List<LocalStore.Entry> habits, String today) {
        Map<String, Row> result = new LinkedHashMap<>();
        for (Row row : saved) result.put(row.key, row);
        for (Task task : tasks) {
            if (task.scheduledDate.isEmpty() || task.scheduledDate.compareTo(today) > 0) continue;
            upsert(result, "task", String.valueOf(task.id), task.scheduledDate, task.title,
                    task.note, task.completed ? 100 : task.progress, today);
        }
        for (LocalStore.Entry habit : habits) {
            if (habit.created == null || habit.created.isEmpty() || habit.created.compareTo(today) > 0) continue;
            try {
                SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT);
                format.setLenient(false);
                Calendar date = Calendar.getInstance(); date.setTime(format.parse(habit.created));
                for (String day = format.format(date.getTime()); day.compareTo(today) <= 0;
                        date.add(Calendar.DAY_OF_MONTH, 1), day = format.format(date.getTime())) {
                    upsert(result, "habit", habit.id, day, habit.title, habit.body,
                            habit.progressOn(day), today);
                }
            } catch (java.text.ParseException error) { throw new IllegalArgumentException("Ngày tạo thói quen không hợp lệ", error); }
        }
        List<Row> rows = new ArrayList<>(result.values());
        Collections.sort(rows, (a, b) -> { int date = b.date.compareTo(a.date); return date != 0 ? date : a.title.compareTo(b.title); });
        return rows;
    }
    private static void upsert(Map<String, Row> rows, String kind, String id, String date,
                               String title, String note, int progress, String today) {
        String key = kind + ":" + id + ":" + date;
        Row old = rows.get(key);
        // Khi sang ngày mới, giữ đúng kết quả đã ghi của ngày trước.
        if (old != null && date.compareTo(today) < 0) return;
        Row row = new Row(); row.key = key; row.kind = kind; row.sourceId = id;
        row.date = date; row.title = title; row.note = note == null ? "" : note;
        row.progress = Math.max(0, Math.min(100, progress)); row.bookmarked = old != null && old.bookmarked;
        rows.put(key, row);
    }
    public void recordTaskResult(Task task, String date) {
        synchronized (DailyHistory.class) {
            Map<String, Row> result = new LinkedHashMap<>();
            for (Row row : rows()) result.put(row.key, row);
            upsert(result, "task", String.valueOf(task.id), date, task.title, task.note,
                    task.completed ? 100 : task.progress, date);
            List<Row> rows = new ArrayList<>(result.values());
            Collections.sort(rows, (a, b) -> b.date.compareTo(a.date)); write(rows);
        }
    }
    public void setHabitResult(LocalStore.Entry habit, String date) {
        synchronized (DailyHistory.class) {
            List<Row> all = rows();
            for (Row row : all) if (row.kind.equals("habit") && row.sourceId.equals(habit.id) && row.date.equals(date))
                row.progress = habit.progressOn(date);
            write(all);
        }
    }
    public void bookmark(Row row) {
        synchronized (DailyHistory.class) {
            List<Row> rows = rows();
            for (Row saved : rows) if (saved.key.equals(row.key)) saved.bookmarked = !saved.bookmarked;
            write(rows);
        }
    }
    public void annotate(Row row, String note) {
        synchronized (DailyHistory.class) {
            List<Row> rows = rows();
            for (Row saved : rows) if (saved.key.equals(row.key)) saved.note = note;
            write(rows);
        }
    }
    private void write(List<Row> rows) {
        JSONArray data = new JSONArray();
        try {
            for (Row row : rows) data.put(new JSONObject().put("key", row.key).put("date", row.date)
                    .put("kind", row.kind).put("sourceId", row.sourceId).put("title", row.title)
                    .put("note", row.note).put("progress", row.progress).put("bookmarked", row.bookmarked));
        } catch (JSONException error) { throw new IllegalStateException(error); }
        String value = data.toString();
        if (!value.equals(preferences.getString("rows", "[]"))) preferences.edit().putString("rows", value).apply();
    }
}
