package vn.edu.taskmanager.data;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Dữ liệu bổ sung cho bản giao diện cục bộ. Công việc vẫn nằm trong Room. */
public class LocalStore {
    public static class Entry {
        public String id, type, title, body, date, time, created;
        public boolean bookmarked;
        public List<String> days = new ArrayList<>();
    }
    private final SharedPreferences preferences;
    public LocalStore(Context context) {
        preferences = context.getSharedPreferences("personal_workspace", Context.MODE_PRIVATE);
    }
    public static String today() { return day(new Date()); }
    public static String day(Date date) {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(date);
    }
    public List<Entry> entries(String type) {
        List<Entry> result = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(preferences.getString("entries", "[]"));
            for (int i = 0; i < array.length(); i++) {
                JSONObject value = array.getJSONObject(i);
                Entry entry = new Entry();
                entry.id = value.getString("id");
                entry.type = value.getString("type");
                entry.title = value.getString("title");
                entry.body = value.optString("body");
                entry.date = value.optString("date");
                entry.time = value.optString("time", "09:00");
                entry.created = value.optString("created", entry.date);
                entry.bookmarked = value.optBoolean("bookmarked");
                JSONArray days = value.optJSONArray("days");
                if (days != null) for (int j = 0; j < days.length(); j++) entry.days.add(days.getString(j));
                if (type == null || type.equals(entry.type)) result.add(entry);
            }
        } catch (JSONException error) {
            throw new IllegalStateException("Không đọc được dữ liệu cục bộ", error);
        }
        return result;
    }
    public Entry find(String id) {
        for (Entry entry : entries(null)) if (entry.id.equals(id)) return entry;
        return null;
    }
    public void save(Entry entry) {
        List<Entry> all = entries(null);
        if (entry.id == null) {
            entry.id = UUID.randomUUID().toString();
            entry.created = today();
        }
        for (int i = all.size() - 1; i >= 0; i--) if (all.get(i).id.equals(entry.id)) all.remove(i);
        all.add(entry);
        write(all);
    }
    public void delete(String id) {
        List<Entry> all = entries(null);
        for (int i = all.size() - 1; i >= 0; i--) if (all.get(i).id.equals(id)) all.remove(i);
        write(all);
    }
    public void checkHabit(Entry entry, String day, boolean checked) {
        entry.days.remove(day);
        if (checked) entry.days.add(day);
        save(entry);
    }
    private void write(List<Entry> all) {
        JSONArray array = new JSONArray();
        try {
            for (Entry entry : all) {
                JSONObject value = new JSONObject();
                value.put("id", entry.id).put("type", entry.type).put("title", entry.title)
                        .put("body", entry.body).put("date", entry.date).put("time", entry.time)
                        .put("created", entry.created).put("bookmarked", entry.bookmarked)
                        .put("days", new JSONArray(entry.days));
                array.put(value);
            }
        } catch (JSONException error) { throw new IllegalStateException(error); }
        preferences.edit().putString("entries", array.toString()).apply();
    }
}
