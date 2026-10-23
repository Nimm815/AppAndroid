package vn.edu.taskmanager.data;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Mô hình dữ liệu cho lời nhắc việc (UC11).
 * Hỗ trợ nhắc một lần, hằng ngày, và chọn các thứ trong tuần.
 */
public class Reminder implements Serializable {
    public static final String REPEAT_ONCE = "once";
    public static final String REPEAT_DAILY = "daily";
    public static final String REPEAT_WEEKDAYS = "weekdays";

    public static final String TARGET_CUSTOM = "custom";
    public static final String TARGET_HABIT = "habit";
    public static final String TARGET_EVENT = "event";

    public String id;
    public String title = "";
    public String targetType = TARGET_CUSTOM; // custom, habit, event
    public String targetId = "";              // ID của thói quen hoặc sự kiện liên kết
    public String targetName = "";
    public String time = "09:00";             // Định dạng HH:mm
    public String date = "";                  // Định dạng yyyy-MM-dd (cho nhắc một lần)
    public String repeatType = REPEAT_DAILY;  // once, daily, weekdays
    public List<Integer> daysOfWeek = new ArrayList<>(); // Calendar.MONDAY..Calendar.SUNDAY
    public boolean isEnabled = true;
    public boolean isFromEvent = false;
    public int eventReminderMinutes = 0;      // 0: đúng giờ, 15: trước 15 phút
    public long nextTriggerMillis = 0;
    public long createdAt = System.currentTimeMillis();

    public Reminder() {
        this.id = UUID.randomUUID().toString();
    }

    public static Reminder fromJson(JSONObject json) {
        Reminder r = new Reminder();
        r.id = json.optString("id", UUID.randomUUID().toString());
        r.title = json.optString("title", "");
        r.targetType = json.optString("targetType", TARGET_CUSTOM);
        r.targetId = json.optString("targetId", "");
        r.targetName = json.optString("targetName", "");
        r.time = json.optString("time", "09:00");
        r.date = json.optString("date", "");
        r.repeatType = json.optString("repeatType", REPEAT_DAILY);
        r.isEnabled = json.optBoolean("isEnabled", true);
        r.isFromEvent = json.optBoolean("isFromEvent", false);
        r.eventReminderMinutes = json.optInt("eventReminderMinutes", 0);
        r.nextTriggerMillis = json.optLong("nextTriggerMillis", 0);
        r.createdAt = json.optLong("createdAt", System.currentTimeMillis());

        r.daysOfWeek = new ArrayList<>();
        JSONArray days = json.optJSONArray("daysOfWeek");
        if (days != null) {
            for (int i = 0; i < days.length(); i++) {
                r.daysOfWeek.add(days.optInt(i));
            }
        }
        return r;
    }

    public JSONObject toJson() {
        JSONObject json = new JSONObject();
        try {
            json.put("id", id);
            json.put("title", title);
            json.put("targetType", targetType);
            json.put("targetId", targetId);
            json.put("targetName", targetName);
            json.put("time", time);
            json.put("date", date);
            json.put("repeatType", repeatType);
            json.put("isEnabled", isEnabled);
            json.put("isFromEvent", isFromEvent);
            json.put("eventReminderMinutes", eventReminderMinutes);
            json.put("nextTriggerMillis", nextTriggerMillis);
            json.put("createdAt", createdAt);

            JSONArray days = new JSONArray();
            if (daysOfWeek != null) {
                for (int d : daysOfWeek) {
                    days.put(d);
                }
            }
            json.put("daysOfWeek", days);
        } catch (JSONException e) {
            // Ignore
        }
        return json;
    }

    /**
     * Mô tả cách lặp để hiển thị trên giao diện người dùng.
     */
    public String getRepeatDescription() {
        if (isFromEvent) {
            return "Liên kết sự kiện";
        }
        if (REPEAT_ONCE.equals(repeatType)) {
            return "Một lần";
        }
        if (REPEAT_DAILY.equals(repeatType)) {
            return "Hằng ngày";
        }
        if (REPEAT_WEEKDAYS.equals(repeatType)) {
            if (daysOfWeek == null || daysOfWeek.isEmpty()) return "Hằng ngày";
            if (daysOfWeek.size() == 7) return "Hằng ngày";
            Collections.sort(daysOfWeek, (a, b) -> {
                int ma = a == Calendar.SUNDAY ? 8 : a;
                int mb = b == Calendar.SUNDAY ? 8 : b;
                return Integer.compare(ma, mb);
            });
            StringBuilder sb = new StringBuilder("Thứ ");
            for (int i = 0; i < daysOfWeek.size(); i++) {
                int day = daysOfWeek.get(i);
                if (i > 0) sb.append(", ");
                if (day == Calendar.SUNDAY) sb.append("CN");
                else sb.append(day);
            }
            return sb.toString();
        }
        return "Một lần";
    }

    /**
     * Tính toán thời điểm thông báo tiếp theo (milis).
     * Trả về 0 nếu lời nhắc đã qua hạn (cho loại một lần).
     */
    public long calculateNextTrigger(long nowMillis) {
        String[] timeParts = time.split(":");
        int hour = Integer.parseInt(timeParts[0]);
        int minute = Integer.parseInt(timeParts[1]);

        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(nowMillis);
        cal.set(Calendar.HOUR_OF_DAY, hour);
        cal.set(Calendar.MINUTE, minute);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);

        if (REPEAT_ONCE.equals(repeatType)) {
            if (date != null && !date.isEmpty()) {
                String[] dateParts = date.split("-");
                cal.set(Calendar.YEAR, Integer.parseInt(dateParts[0]));
                cal.set(Calendar.MONTH, Integer.parseInt(dateParts[1]) - 1);
                cal.set(Calendar.DAY_OF_MONTH, Integer.parseInt(dateParts[2]));
            }
            if (cal.getTimeInMillis() <= nowMillis) {
                return 0; // Đã quá hạn
            }
            return cal.getTimeInMillis();
        }

        if (REPEAT_DAILY.equals(repeatType)) {
            if (cal.getTimeInMillis() <= nowMillis) {
                cal.add(Calendar.DAY_OF_MONTH, 1);
            }
            return cal.getTimeInMillis();
        }

        if (REPEAT_WEEKDAYS.equals(repeatType)) {
            if (daysOfWeek == null || daysOfWeek.isEmpty()) {
                if (cal.getTimeInMillis() <= nowMillis) {
                    cal.add(Calendar.DAY_OF_MONTH, 1);
                }
                return cal.getTimeInMillis();
            }

            // Tìm ngày gần nhất trong tuần được chọn
            for (int dayOffset = 0; dayOffset < 8; dayOffset++) {
                Calendar test = (Calendar) cal.clone();
                test.add(Calendar.DAY_OF_MONTH, dayOffset);
                int dayOfWeek = test.get(Calendar.DAY_OF_WEEK);
                if (daysOfWeek.contains(dayOfWeek)) {
                    if (test.getTimeInMillis() > nowMillis) {
                        return test.getTimeInMillis();
                    }
                }
            }
        }

        return 0;
    }

    /**
     * Định dạng chuỗi hiển thị lần nhắc tiếp theo (Ví dụ: "Hôm nay, 20:00" hoặc "Thứ 4, 07:00" hoặc "Đã kết thúc").
     */
    public String getNextTriggerDisplay() {
        if (!isEnabled) {
            return "Đã tắt";
        }
        if (nextTriggerMillis <= 0) {
            return "Đã kết thúc";
        }
        Calendar now = Calendar.getInstance();
        Calendar target = Calendar.getInstance();
        target.setTimeInMillis(nextTriggerMillis);

        SimpleDateFormat timeFmt = new SimpleDateFormat("HH:mm", Locale.ROOT);
        String timeStr = timeFmt.format(target.getTime());

        if (now.get(Calendar.YEAR) == target.get(Calendar.YEAR) &&
            now.get(Calendar.DAY_OF_YEAR) == target.get(Calendar.DAY_OF_YEAR)) {
            return "Hôm nay, " + timeStr;
        }

        Calendar tomorrow = (Calendar) now.clone();
        tomorrow.add(Calendar.DAY_OF_YEAR, 1);
        if (tomorrow.get(Calendar.YEAR) == target.get(Calendar.YEAR) &&
            tomorrow.get(Calendar.DAY_OF_YEAR) == target.get(Calendar.DAY_OF_YEAR)) {
            return "Ngày mai, " + timeStr;
        }

        SimpleDateFormat dateFmt = new SimpleDateFormat("dd/MM", Locale.ROOT);
        return dateFmt.format(target.getTime()) + ", " + timeStr;
    }
}
