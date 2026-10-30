package vn.edu.taskmanager.sync;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.*;
import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;
import retrofit2.*;
import retrofit2.http.*;
import retrofit2.converter.gson.GsonConverterFactory;
import vn.edu.taskmanager.data.*;

/** Offline-first sync for the Android workspace, split across Task and Productivity services. */
public final class WorkspaceSyncWorker extends Worker {
    interface Api {
        @GET("api/native-workspace/{device}") Call<Map<String, Object>> read(@Path("device") String device);
        @PUT("api/native-workspace/{device}") Call<Map<String, Object>> save(
                @Path("device") String device, @Body Map<String, Object> body);
    }

    private static final Type MAP_TYPE = new TypeToken<Map<String, Object>>() { }.getType();
    private final Gson gson = new Gson();

    public WorkspaceSyncWorker(@NonNull Context context, @NonNull WorkerParameters parameters) {
        super(context, parameters);
    }

    @NonNull @Override public Result doWork() {
        Context context = getApplicationContext();
        SharedPreferences state = WorkspaceSync.state(context);
        if (!WorkspaceSync.configured(context)) {
            state.edit().putString("status", "Chưa cấu hình địa chỉ Task và Productivity API trong bản build.").apply();
            return Result.success();
        }
        if (!vn.edu.taskmanager.auth.AccountSession.isLoggedIn(context)) {
            String status = vn.edu.taskmanager.auth.AccountSession.authenticationRequired(context)
                    ? "Đăng nhập lại để tiếp tục đồng bộ; mọi dữ liệu vẫn được giữ trên điện thoại."
                    : "Đăng nhập để đồng bộ lên máy chủ; dữ liệu hiện vẫn được giữ trên điện thoại.";
            state.edit().putString("status", status).apply();
            return Result.success();
        }
        try {
            String device = WorkspaceSync.device(context);
            Api taskApi = api(vn.edu.taskmanager.BuildConfig.TASK_URL, context);
            Api productivityApi = api(vn.edu.taskmanager.BuildConfig.HABIT_URL, context);
            LegacyTaskListImporter.importOnce(context, TaskDatabase.getInstance(context));

            Map<String, Object> taskSnapshot = taskSnapshot(context);
            Map<String, Object> productivitySnapshot = productivitySnapshot(context);
            long revision = state.getLong("revision", 0);
            long taskAck = state.getLong("task_acknowledged", state.getLong("acknowledged", -1));
            long productivityAck = state.getLong("productivity_acknowledged", -1);

            Map<String, Object> remoteTask = read(taskApi, device, "Task Service");
            Map<String, Object> remoteProductivity = read(productivityApi, device, "Productivity Service");
            long remoteTaskRevision = revision(remoteTask);
            long remoteProductivityRevision = revision(remoteProductivity);

            boolean pulled = false;
            if (remoteTask != null && remoteTaskRevision > taskAck) {
                if (remoteTaskRevision > revision) pulled = true;
                applyTaskSnapshot(context, payload(remoteTask), false);
                taskAck = remoteTaskRevision;
                revision = Math.max(revision, remoteTaskRevision);
                taskSnapshot = taskSnapshot(context);
            }
            if (remoteProductivity != null && remoteProductivityRevision > productivityAck) {
                if (remoteProductivityRevision > revision) pulled = true;
                applyProductivitySnapshot(context, payload(remoteProductivity), false);
                productivityAck = remoteProductivityRevision;
                revision = Math.max(revision, remoteProductivityRevision);
                productivitySnapshot = productivitySnapshot(context);
            }
            if (pulled) revision = Math.max(revision, Math.max(remoteTaskRevision, remoteProductivityRevision)) + 1;
            if (revision != state.getLong("revision", 0)) state.edit().putLong("revision", revision).apply();
            taskSnapshot.put("revision", revision);
            productivitySnapshot.put("revision", revision);

            if (revision > taskAck) {
                long saved = save(taskApi, device, taskSnapshot, "Task Service");
                if (saved != revision) throw new SyncException("Task Service từ chối bản chụp cũ; sẽ tải lại dữ liệu mới nhất.");
                taskAck = saved;
                state.edit().putLong("task_acknowledged", taskAck).apply();
            }
            if (revision > productivityAck) {
                long saved = save(productivityApi, device, productivitySnapshot, "Productivity Service");
                if (saved != revision) throw new SyncException("Productivity Service từ chối bản chụp cũ; sẽ tải lại dữ liệu mới nhất.");
                productivityAck = saved;
                state.edit().putLong("productivity_acknowledged", productivityAck).apply();
            }

            long acknowledged = Math.min(taskAck, productivityAck);
            state.edit().putLong("acknowledged", acknowledged)
                    .putString("status", "Đã đồng bộ công việc, thói quen, nhật ký, lịch, nhắc việc và lịch sử ngày lên MySQL.")
                    .apply();
            return Result.success();
        } catch (Exception error) {
            if (isStopped()) return Result.failure();
            String message = error instanceof SyncException ? error.getMessage()
                    : "Không thể kết nối hoặc đồng bộ với máy chủ: " + safeMessage(error);
            state.edit().putString("status", "Chưa đồng bộ MySQL: " + message + ". Dữ liệu cục bộ vẫn được giữ; ứng dụng sẽ thử lại.").apply();
            return Result.retry();
        }
    }

    private Api api(String baseUrl, Context context) {
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS).retryOnConnectionFailure(false)
                .addInterceptor(chain -> {
                    String token = vn.edu.taskmanager.auth.Session.token(context);
                    okhttp3.Request.Builder request = chain.request().newBuilder();
                    if (token != null && !token.isEmpty()) request.header("Authorization", "Bearer " + token);
                    return chain.proceed(request.build());
                }).build();
        return new Retrofit.Builder().baseUrl(baseUrl).client(client)
                .addConverterFactory(GsonConverterFactory.create()).build().create(Api.class);
    }

    private Map<String, Object> read(Api api, String device, String name) throws Exception {
        Response<Map<String, Object>> response = api.read(device).execute();
        if (response.code() == 404) { if (response.errorBody() != null) response.errorBody().close(); return null; }
        if (!response.isSuccessful() || response.body() == null) throw failure(name, response);
        return response.body();
    }

    private long save(Api api, String device, Map<String, Object> body, String name) throws Exception {
        Response<Map<String, Object>> response = api.save(device, body).execute();
        if (!response.isSuccessful() || response.body() == null) throw failure(name, response);
        Object value = response.body().get("revision");
        return value instanceof Number ? ((Number) value).longValue() : -1;
    }

    private SyncException failure(String name, Response<?> response) {
        String detail = "HTTP " + response.code();
        if (response.errorBody() != null) {
            try {
                String text = response.errorBody().string();
                Map<String, Object> body = gson.fromJson(text, MAP_TYPE);
                if (body != null && body.get("message") != null) detail = String.valueOf(body.get("message"));
            } catch (Exception ignored) { }
        }
        return new SyncException(name + ": " + detail);
    }

    private long revision(Map<String, Object> response) {
        if (response == null || !(response.get("revision") instanceof Number)) return -1;
        return ((Number) response.get("revision")).longValue();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> payload(Map<String, Object> response) {
        if (response == null) return Collections.emptyMap();
        Object raw = response.get("payload");
        if (raw instanceof Map) return (Map<String, Object>) raw;
        if (raw instanceof String && !((String) raw).isBlank()) {
            Map<String, Object> parsed = gson.fromJson((String) raw, MAP_TYPE);
            return parsed == null ? Collections.emptyMap() : parsed;
        }
        return Collections.emptyMap();
    }

    private Map<String, Object> taskSnapshot(Context context) {
        Map<String, Object> body = new LinkedHashMap<>();
        List<Map<String, Object>> tasks = new ArrayList<>(), lists = new ArrayList<>();
        TaskDatabase db = TaskDatabase.getInstance(context);
        db.runInTransaction(() -> {
            for (Task task : db.taskDao().allTasks()) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("id", String.valueOf(task.id)); item.put("title", task.title);
                item.put("scheduledDate", task.scheduledDate); item.put("priority", task.priority);
                item.put("progress", task.progress); item.put("note", task.note); item.put("createdAt", task.createdAt);
                tasks.add(item);
            }
            for (TaskListWithTasks list : db.taskListDao().allLists()) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("id", list.list.id); item.put("title", list.list.title);
                item.put("createdAt", list.list.createdAt);
                List<String> members = new ArrayList<>();
                for (Task task : list.tasks) members.add(String.valueOf(task.id));
                item.put("members", members); lists.add(item);
            }
        });
        body.put("revision", WorkspaceSync.state(context).getLong("revision", 0));
        body.put("tasks", tasks); body.put("lists", lists);
        return body;
    }

    private Map<String, Object> productivitySnapshot(Context context) {
        Map<String, Object> body = new LinkedHashMap<>();
        LocalStore store = new LocalStore(context);
        List<Map<String, Object>> habits = new ArrayList<>(), notes = new ArrayList<>(), events = new ArrayList<>();
        Set<String> apiHabitIds = new HashSet<>();
        for (LocalStore.Entry entry : store.entries(null)) {
            if ("habit".equals(entry.type) && !"server_api".equals(entry.sourceType)) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("id", entry.id); item.put("title", entry.title); item.put("body", entry.body);
                item.put("created", entry.created == null || entry.created.isBlank() ? LocalStore.today() : entry.created);
                Map<String, Integer> progress = new LinkedHashMap<>(entry.dailyProgress);
                for (String completed : entry.days) progress.put(completed, 100);
                item.put("progress", progress); item.put("icon", entry.icon); item.put("color", entry.color);
                item.put("weekdays", entry.weekdays); item.put("scheduleConfigured", entry.scheduleConfigured);
                item.put("targetCount", entry.targetCount); item.put("targetUnit", entry.targetUnit);
                item.put("reminderEnabled", entry.reminderEnabled); item.put("reminderTime", entry.reminderTime);
                habits.add(item);
            } else if ("habit".equals(entry.type) && "server_api".equals(entry.sourceType)) {
                apiHabitIds.add(entry.id);
            } else if ("note".equals(entry.type)) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("id", entry.id); item.put("title", entry.title); item.put("content", entry.body);
                item.put("entryDate", dateTime(entry.date, entry.time)); item.put("bookmarked", entry.bookmarked);
                item.put("label", entry.label); item.put("sourceType", entry.sourceType);
                item.put("sourceLocalId", entry.sourceId); item.put("progress", entry.progress);
                notes.add(item);
            } else if ("event".equals(entry.type)) {
                boolean allDay = "all_day".equals(entry.label) || entry.progress == 1;
                String start = dateTime(entry.date, allDay ? "00:00" : entry.time);
                String end = allDay ? dateTime(LocalStore.day(addDays(entry.date, 1)), "00:00")
                        : dateTime(entry.date, entry.label != null && entry.label.startsWith("end:")
                                ? entry.label.substring(4) : addMinutes(entry.time, 30));
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("id", entry.id); item.put("title", entry.title); item.put("description", entry.body);
                item.put("startTime", start); item.put("endTime", end); item.put("allDay", allDay);
                item.put("relatedHabitLocalId", "habit".equals(entry.sourceType) ? entry.sourceId : "");
                events.add(item);
            }
        }
        List<Map<String, Object>> reminders = new ArrayList<>();
        for (Reminder reminder : store.reminders()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", reminder.id); item.put("title", reminder.title); item.put("targetType", reminder.targetType);
            item.put("targetId", reminder.targetId); item.put("targetName", reminder.targetName);
            item.put("time", reminder.time); item.put("date", reminder.date); item.put("repeatType", reminder.repeatType);
            item.put("daysOfWeek", reminder.daysOfWeek); item.put("enabled", reminder.isEnabled);
            item.put("fromEvent", reminder.isFromEvent); item.put("eventReminderMinutes", reminder.eventReminderMinutes);
            item.put("nextTriggerMillis", reminder.nextTriggerMillis); item.put("createdAt", reminder.createdAt);
            reminders.add(item);
        }
        List<Map<String, Object>> history = new ArrayList<>();
        for (DailyHistory.Row row : new DailyHistory(context).rows()) {
            if ("habit".equals(row.kind) && row.sourceId != null
                    && (row.sourceId.startsWith("api-habit-") || apiHabitIds.contains(row.sourceId))) continue;
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("key", row.key); item.put("date", row.date); item.put("kind", row.kind);
            item.put("sourceId", row.sourceId); item.put("title", row.title); item.put("note", row.note);
            item.put("progress", row.progress); item.put("bookmarked", row.bookmarked); history.add(item);
        }
        body.put("revision", WorkspaceSync.state(context).getLong("revision", 0));
        body.put("habits", habits); body.put("notes", notes); body.put("events", events);
        body.put("reminders", reminders); body.put("history", history);
        return body;
    }

    private void applyTaskSnapshot(Context context, Map<String, Object> payload, boolean replace) {
        TaskDatabase db = TaskDatabase.getInstance(context);
        List<Map<String, Object>> tasks = maps(payload.get("tasks"));
        List<Map<String, Object>> lists = maps(payload.get("lists"));
        db.runInTransaction(() -> {
            for (Map<String, Object> value : lists) {
                String id = string(value.get("id"));
                if (id.isBlank() || (!replace && db.taskListDao().find(id) != null)) continue;
                TaskList list = new TaskList(); list.id = id; list.title = string(value.get("title"));
                list.createdAt = number(value.get("createdAt"), System.currentTimeMillis());
                db.taskListDao().insertFromSync(list);
            }
            for (Map<String, Object> value : tasks) {
                long id = longValue(value.get("id"), -1);
                if (id < 1 || (!replace && db.taskDao().findById(id) != null)) continue;
                Task task = new Task(string(value.get("title"))); task.id = id;
                task.scheduledDate = string(value.get("scheduledDate")); task.priority = (int) number(value.get("priority"), 2);
                task.progress = (int) number(value.get("progress"), 0); task.completed = task.progress >= 100;
                task.note = string(value.get("note")); task.createdAt = number(value.get("createdAt"), System.currentTimeMillis());
                db.taskDao().insertFromSync(task);
            }
            for (Map<String, Object> listValue : lists) {
                String listId = string(listValue.get("id"));
                if (db.taskListDao().find(listId) == null) continue;
                for (Object member : list(listValue.get("members"))) {
                    long taskId;
                    try { taskId = Long.parseLong(String.valueOf(member)); }
                    catch (NumberFormatException ignored) { continue; }
                    if (db.taskDao().findById(taskId) != null && db.taskListDao().membershipCount(taskId) == 0)
                        db.taskListDao().addMember(new TaskListMember(listId, taskId));
                }
            }
        });
    }

    private void applyProductivitySnapshot(Context context, Map<String, Object> payload, boolean replace) {
        LocalStore store = new LocalStore(context);
        for (Map<String, Object> value : maps(payload.get("habits"))) {
            String id = string(value.get("id"));
            if (id.isBlank() || (!replace && store.find(id) != null)) continue;
            LocalStore.Entry entry = new LocalStore.Entry(); entry.id = id; entry.type = "habit";
            entry.title = string(value.get("title")); entry.body = string(value.get("body"));
            entry.created = string(value.get("created")); entry.date = entry.created; entry.icon = stringOr(value.get("icon"), "🌱");
            entry.color = stringOr(value.get("color"), "#5B6FDE"); entry.weekdays = (int) number(value.get("weekdays"), 127);
            entry.scheduleConfigured = bool(value.get("scheduleConfigured"), true);
            entry.targetCount = (int) number(value.get("targetCount"), 1); entry.targetUnit = stringOr(value.get("targetUnit"), "lần");
            entry.reminderEnabled = bool(value.get("reminderEnabled"), false); entry.reminderTime = string(value.get("reminderTime"));
            Map<String, Object> progress = object(value.get("progress"));
            for (Map.Entry<String, Object> day : progress.entrySet()) entry.dailyProgress.put(day.getKey(), (int) number(day.getValue(), 0));
            for (Map.Entry<String, Integer> day : entry.dailyProgress.entrySet()) if (day.getValue() >= 100) entry.days.add(day.getKey());
            store.saveFromSync(entry);
        }
        for (Map<String, Object> value : maps(payload.get("notes"))) {
            String id = string(value.get("id"));
            if (id.isBlank() || (!replace && store.find(id) != null)) continue;
            LocalStore.Entry entry = new LocalStore.Entry(); entry.id = id; entry.type = "note";
            entry.title = string(value.get("title")); entry.body = string(value.get("content"));
            entry.bookmarked = bool(value.get("bookmarked"), false); setDateTime(entry, string(value.get("entryDate")));
            entry.label = string(value.get("label")); entry.sourceType = string(value.get("sourceType"));
            entry.sourceId = string(value.get("sourceLocalId")); entry.progress = (int) number(value.get("progress"), -1);
            store.saveFromSync(entry);
        }
        for (Map<String, Object> value : maps(payload.get("events"))) {
            String id = string(value.get("id"));
            if (id.isBlank() || (!replace && store.find(id) != null)) continue;
            LocalStore.Entry entry = new LocalStore.Entry(); entry.id = id; entry.type = "event";
            entry.title = string(value.get("title")); entry.body = string(value.get("description"));
            boolean allDay = bool(value.get("allDay"), false); setDateTime(entry, string(value.get("startTime")));
            entry.label = allDay ? "all_day" : "end:" + string(value.get("endTime")).substring(11, 16);
            entry.progress = allDay ? 1 : 0;
            String linkedHabit = string(value.get("relatedHabitLocalId"));
            if (!linkedHabit.isBlank()) { entry.sourceId = linkedHabit; entry.sourceType = "habit"; }
            store.saveFromSync(entry);
        }
        for (Map<String, Object> value : maps(payload.get("reminders"))) {
            String id = string(value.get("id"));
            if (id.isBlank() || (!replace && store.findReminder(id) != null)) continue;
            try {
                Reminder reminder = Reminder.fromJson(new org.json.JSONObject(gson.toJson(value)));
                reminder.isEnabled = bool(value.get("enabled"), reminder.isEnabled);
                reminder.isFromEvent = bool(value.get("fromEvent"), false);
                vn.edu.taskmanager.reminder.ReminderScheduler.scheduleFromSync(context, reminder);
                store.saveReminderFromSync(reminder);
            } catch (org.json.JSONException ignored) { }
        }
        List<DailyHistory.Row> rows = new ArrayList<>();
        for (Map<String, Object> value : maps(payload.get("history"))) {
            DailyHistory.Row row = new DailyHistory.Row(); row.key = string(value.get("key"));
            row.date = string(value.get("date")); row.kind = string(value.get("kind"));
            row.sourceId = string(value.get("sourceId")); row.title = string(value.get("title"));
            row.note = string(value.get("note")); row.progress = (int) number(value.get("progress"), 0);
            row.bookmarked = bool(value.get("bookmarked"), false); if (!row.key.isBlank()) rows.add(row);
        }
        new DailyHistory(context).mergeFromSync(rows);
    }

    private void setDateTime(LocalStore.Entry entry, String value) {
        if (value == null || value.length() < 16) { entry.date = LocalStore.today(); entry.time = "09:00"; return; }
        entry.date = value.substring(0, 10); entry.time = value.substring(11, 16);
    }
    private String dateTime(String date, String time) {
        String day = date == null || date.isBlank() ? LocalStore.today() : date;
        String at = time == null || time.length() < 5 ? "09:00" : time.substring(0, 5);
        return day + "T" + at + ":00";
    }
    private Date addDays(String date, int amount) {
        try { java.text.SimpleDateFormat format = new java.text.SimpleDateFormat("yyyy-MM-dd", Locale.ROOT); format.setLenient(false);
            Calendar c = Calendar.getInstance(); c.setTime(format.parse(date)); c.add(Calendar.DAY_OF_MONTH, amount); return c.getTime();
        } catch (Exception e) { return new Date(); }
    }
    private String addMinutes(String time, int amount) {
        try { String[] parts = time.split(":"); Calendar c = Calendar.getInstance();
            c.set(Calendar.HOUR_OF_DAY, Integer.parseInt(parts[0])); c.set(Calendar.MINUTE, Integer.parseInt(parts[1]));
            c.add(Calendar.MINUTE, amount); return String.format(Locale.ROOT, "%02d:%02d", c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE));
        } catch (Exception e) { return "09:30"; }
    }
    private List<?> list(Object value) { return value instanceof List ? (List<?>) value : Collections.emptyList(); }
    @SuppressWarnings("unchecked") private List<Map<String, Object>> maps(Object value) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : list(value)) if (item instanceof Map) result.add((Map<String, Object>) item);
        return result;
    }
    @SuppressWarnings("unchecked") private Map<String, Object> object(Object value) {
        return value instanceof Map ? (Map<String, Object>) value : Collections.emptyMap();
    }
    private String string(Object value) { return value == null ? "" : String.valueOf(value); }
    private String stringOr(Object value, String fallback) { String result = string(value); return result.isBlank() ? fallback : result; }
    private long number(Object value, long fallback) { return value instanceof Number ? ((Number) value).longValue() : fallback; }
    private long longValue(Object value, long fallback) {
        if (value instanceof Number) return ((Number) value).longValue();
        try { return Long.parseLong(String.valueOf(value)); }
        catch (RuntimeException ignored) { return fallback; }
    }
    private boolean bool(Object value, boolean fallback) { return value instanceof Boolean ? (Boolean) value : fallback; }
    private String safeMessage(Exception error) { return error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage(); }
    private static final class SyncException extends Exception { SyncException(String message) { super(message); } }
}
