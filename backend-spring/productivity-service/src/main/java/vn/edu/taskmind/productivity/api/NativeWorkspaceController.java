package vn.edu.taskmind.productivity.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.context.annotation.Profile;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.*;
import java.util.*;

/** Device snapshot endpoint used by the Android app's offline-first workspace. */
@RestController
@Profile("dev")
@RequestMapping("/api/native-workspace")
public class NativeWorkspaceController {
    public record HabitInput(
            @NotBlank @Size(max = 100) String id,
            @NotBlank @Size(max = 200) String title,
            @Size(max = 10000) String body,
            @NotNull LocalDate created,
            @NotNull @Size(max = 40000) Map<@NotBlank String, @NotNull @Min(0) @Max(100) Integer> progress,
            String icon, String color, Integer weekdays, Boolean scheduleConfigured,
            Integer targetCount, String targetUnit, Boolean reminderEnabled, String reminderTime) { }

    public record JournalInput(@NotBlank @Size(max = 100) String id,
            @NotBlank @Size(max = 200) String title, @NotNull @Size(max = 2000) String content,
            @NotNull LocalDateTime entryDate, boolean bookmarked, @Size(max = 200) String label,
            @Size(max = 16) String sourceType, @Size(max = 100) String sourceLocalId,
            @Min(-1) @Max(100) int progress) { }

    public record EventInput(@NotBlank @Size(max = 100) String id,
            @NotBlank @Size(max = 200) String title, @Size(max = 2000) String description,
            @NotNull LocalDateTime startTime, @NotNull LocalDateTime endTime,
            boolean allDay, String relatedHabitLocalId) { }

    public record ReminderInput(@NotBlank @Size(max = 100) String id,
            @NotBlank @Size(max = 200) String title,
            @NotBlank @Pattern(regexp = "custom|habit|event|task") String targetType,
            @Size(max = 100) String targetId, @Size(max = 200) String targetName,
            @NotBlank @Size(max = 5) String time, String date,
            @NotBlank @Pattern(regexp = "once|daily|weekdays") String repeatType,
            List<Integer> daysOfWeek, boolean enabled, boolean fromEvent,
            @Min(0) @Max(15) int eventReminderMinutes, @Min(0) long nextTriggerMillis,
            @Min(0) long createdAt) { }

    public record HistoryInput(@NotBlank @Size(max = 200) String key,
            @NotNull LocalDate date, @NotBlank @Size(max = 16) String kind,
            @NotBlank @Size(max = 100) String sourceId, @NotBlank @Size(max = 200) String title,
            @Size(max = 10000) String note, @Min(0) @Max(100) int progress,
            boolean bookmarked) { }

    public record Snapshot(@Min(0) long revision,
            @Size(max = 2000) List<@Valid HabitInput> habits,
            @Size(max = 10000) List<@Valid JournalInput> notes,
            @Size(max = 10000) List<@Valid EventInput> events,
            @Size(max = 10000) List<@Valid ReminderInput> reminders,
            @Size(max = 40000) List<@Valid HistoryInput> history) { }

    private final DeviceWorkspaceStore store;
    private final ObjectMapper json;

    public NativeWorkspaceController(DeviceWorkspaceStore store, ObjectMapper json) {
        this.store = store;
        this.json = json;
    }

    @GetMapping("/{device}")
    public Map<String, Object> get(@PathVariable String device) { return store.read(device); }

    @PutMapping("/{device}")
    @Transactional
    public Map<String, Object> save(@PathVariable String device, @Valid @RequestBody Snapshot body) throws Exception {
        long previous = store.lock(device);
        if (body.revision() <= previous) return Map.of("revision", previous);

        List<HabitInput> habits = safe(body.habits());
        List<JournalInput> notes = safe(body.notes());
        List<EventInput> events = safe(body.events());
        List<ReminderInput> reminders = safe(body.reminders());
        List<HistoryInput> history = safe(body.history());
        unique(habits.stream().map(HabitInput::id).toList());
        unique(notes.stream().map(JournalInput::id).toList());
        unique(events.stream().map(EventInput::id).toList());
        unique(reminders.stream().map(ReminderInput::id).toList());
        unique(history.stream().map(HistoryInput::key).toList());

        var db = store.db;
        int user = store.user.id();
        Map<String, Integer> habitIds = saveHabits(device, user, habits);
        saveNotes(device, user, notes);
        Map<String, Integer> eventIds = saveEvents(device, user, events, habitIds);
        saveReminders(device, user, reminders, eventIds);
        saveHistory(device, user, history);

        removeAbsent(device, "reminder", ids(reminders), id -> {
            db.update("UPDATE reminders SET IsEnabled=0,EventID=NULL WHERE ReminderID=? AND UserID=?", id, user);
        });
        removeAbsent(device, "event", ids(events), id -> db.update(
                "DELETE FROM calendarevents WHERE EventID=? AND UserID=?", id, user));
        removeAbsent(device, "journal", ids(notes), id -> db.update(
                "DELETE FROM journalentries WHERE EntryID=? AND UserID=?", id, user));
        removeAbsentHistory(user, device, history);
        removeAbsent(device, "habit", ids(habits), id -> db.update(
                "UPDATE habits SET IsActive=0 WHERE HabitID=? AND UserID=?", id, user));

        store.finish(device, body.revision(), json.writeValueAsString(body));
        return Map.of("revision", body.revision(), "habits", habits.size(), "notes", notes.size(),
                "events", events.size(), "reminders", reminders.size(), "history", history.size());
    }

    private Map<String, Integer> saveHabits(String device, int user, List<HabitInput> values) {
        var db = store.db;
        Map<String, Integer> ids = new HashMap<>();
        for (HabitInput h : values) {
            Integer id = store.id(device, "habit", h.id());
            boolean configured = !Boolean.FALSE.equals(h.scheduleConfigured());
            int weekdays = h.weekdays() == null ? 127 : h.weekdays();
            int target = h.targetCount() == null ? 1 : h.targetCount();
            if (id == null) id = store.create(device, "habit",
                    h.id(), "INSERT INTO habits(UserID,HabitName,Frequency,TargetCount) VALUES (?,?,?,?)",
                    user, h.title(), weekdays == 127 ? "DAILY" : "WEEKLY", target);
            db.update("UPDATE habits SET HabitName=?,Description=?,Icon=?,Color=?,Weekdays=?,StartDate=?,"
                            + "TargetCount=?,TargetUnit=?,ReminderTime=?,ReminderEnabled=?,Frequency=?,IsActive=1 "
                            + "WHERE HabitID=? AND UserID=?",
                    h.title(), emptyToNull(h.body()), defaultValue(h.icon(), "🌱"), defaultValue(h.color(), "#5B6FDE"),
                    configured ? weekdays : null, h.created(), target, defaultValue(h.targetUnit(), "lần"),
                    parseTime(h.reminderTime()), Boolean.TRUE.equals(h.reminderEnabled()) && h.reminderTime() != null,
                    weekdays == 127 ? "DAILY" : "WEEKLY", id, user);
            saveHabitSchedule(id, h.created(), configured ? weekdays : 127);
            db.update("DELETE FROM habitlogs WHERE HabitID=?", id);
            for (var item : h.progress().entrySet()) {
                LocalDate day = LocalDate.parse(item.getKey());
                int percent = Math.max(0, Math.min(100, item.getValue()));
                int count = (target * percent + 50) / 100;
                String status = percent >= 100 ? "COMPLETED" : percent == 0 ? "PENDING" : "IN_PROGRESS";
                db.update("INSERT INTO habitlogs(HabitID,LogDate,Status,ActualCount) VALUES (?,?,?,?)",
                        id, day, status, count);
            }
            ids.put(h.id(), id);
        }
        return ids;
    }

    private void saveNotes(String device, int user, List<JournalInput> values) {
        var db = store.db;
        for (JournalInput note : values) {
            Integer id = store.id(device, "journal", note.id());
            if (id == null) id = store.create(device, "journal",
                    note.id(), "INSERT INTO journalentries(UserID,Title,Content,EntryDate,IsBookmarked,Label,SourceType,SourceLocalID,Progress) VALUES (?,?,?,?,?,?,?,?,?)",
                    user, note.title(), note.content(), note.entryDate(), note.bookmarked(), defaultValue(note.label(), ""),
                    defaultValue(note.sourceType(), ""), defaultValue(note.sourceLocalId(), ""), note.progress());
            db.update("UPDATE journalentries SET Title=?,Content=?,EntryDate=?,IsBookmarked=?,Label=?,SourceType=?,SourceLocalID=?,Progress=? WHERE EntryID=? AND UserID=?",
                    note.title(), note.content(), note.entryDate(), note.bookmarked(), defaultValue(note.label(), ""),
                    defaultValue(note.sourceType(), ""), defaultValue(note.sourceLocalId(), ""), note.progress(), id, user);
        }
    }

    private void saveHabitSchedule(int habitId, LocalDate created, int weekdays) {
        var db = store.db;
        var versions = db.queryForList("SELECT EffectiveDate,Weekdays FROM habit_schedule_versions WHERE HabitID=? ORDER BY EffectiveDate", habitId);
        if (versions.isEmpty()) {
            db.update("INSERT INTO habit_schedule_versions(HabitID,EffectiveDate,Weekdays) VALUES (?,?,?)",
                    habitId, created, weekdays);
            return;
        }
        var latest = versions.get(versions.size() - 1);
        int priorDays = ((Number) latest.get("Weekdays")).intValue();
        if (priorDays == weekdays) return;
        LocalDate effectiveDate = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));
        if (effectiveDate.isBefore(created)) effectiveDate = created;
        db.update("INSERT INTO habit_schedule_versions(HabitID,EffectiveDate,Weekdays) VALUES (?,?,?) "
                        + "ON DUPLICATE KEY UPDATE Weekdays=VALUES(Weekdays)",
                habitId, effectiveDate, weekdays);
    }

    private Map<String, Integer> saveEvents(String device, int user, List<EventInput> values,
                                             Map<String, Integer> habitIds) {
        var db = store.db;
        Map<String, Integer> eventIds = new HashMap<>();
        Map<String, Integer> knownHabits = store.records(device, "habit");
        knownHabits.putAll(habitIds);
        for (EventInput event : values) {
            if (!event.endTime().isAfter(event.startTime())) throw new IllegalArgumentException("Event end must be after start");
            Integer relatedHabit = resolveHabit(event.relatedHabitLocalId(), knownHabits);
            Integer id = store.id(device, "event", event.id());
            if (id == null) id = store.create(device, "event",
                    event.id(), "INSERT INTO calendarevents(UserID,Title,StartTime,EndTime) VALUES (?,?,?,?)",
                    user, event.title(), event.startTime(), event.endTime());
            db.update("UPDATE calendarevents SET Title=?,Description=?,StartTime=?,EndTime=?,IsAllDay=?,RelatedHabitID=? "
                            + "WHERE EventID=? AND UserID=?",
                    event.title(), emptyToNull(event.description()), event.startTime(), event.endTime(), event.allDay(), relatedHabit, id, user);
            eventIds.put(event.id(), id);
        }
        return eventIds;
    }

    private void saveReminders(String device, int user, List<ReminderInput> values, Map<String, Integer> eventIds) {
        var db = store.db;
        for (ReminderInput reminder : values) {
            if (reminder.eventReminderMinutes() != 0 && reminder.eventReminderMinutes() != 15)
                throw new IllegalArgumentException("Event reminder must be 0 or 15 minutes");
            Integer event = "event".equals(reminder.targetType()) ? eventIds.get(reminder.targetId()) : null;
            Integer id = store.id(device, "reminder", reminder.id());
            LocalDateTime remindAt = reminder.nextTriggerMillis() > 0
                    ? LocalDateTime.ofInstant(Instant.ofEpochMilli(reminder.nextTriggerMillis()), ZoneId.systemDefault())
                    : reminderDateTime(reminder.date(), reminder.time());
            int mask = 0;
            if (reminder.daysOfWeek() != null) for (Integer day : reminder.daysOfWeek()) {
                if (day == null) continue;
                int index = day == Calendar.SUNDAY ? 6 : day - Calendar.MONDAY;
                if (index >= 0 && index < 7) mask |= 1 << index;
            }
            LocalDateTime created = reminder.createdAt() > 0
                    ? LocalDateTime.ofInstant(Instant.ofEpochMilli(reminder.createdAt()), ZoneId.systemDefault())
                    : LocalDateTime.now();
            if (id == null) id = store.create(device, "reminder",
                    reminder.id(), "INSERT INTO reminders(UserID,EventID,RemindAt,IsEnabled,TargetType,TargetLocalID,TargetName,RepeatType,RepeatDays,EventReminderMinutes,CreatedAt) "
                            + "VALUES (?,?,?,?,?,?,?,?,?,?,?)",
                    user, event, remindAt, reminder.enabled(), reminder.targetType(), reminder.targetId(),
                    defaultValue(reminder.targetName(), ""), reminder.repeatType(), mask, reminder.eventReminderMinutes(), created);
            db.update("UPDATE reminders SET TaskID=NULL,EventID=?,RemindAt=?,IsEnabled=?,TargetType=?,TargetLocalID=?,TargetName=?,"
                            + "RepeatType=?,RepeatDays=?,EventReminderMinutes=? WHERE ReminderID=? AND UserID=?",
                    event, remindAt, reminder.enabled(), reminder.targetType(), reminder.targetId(),
                    defaultValue(reminder.targetName(), ""), reminder.repeatType(), mask,
                    reminder.eventReminderMinutes(), id, user);
        }
    }

    private void saveHistory(String device, int user, List<HistoryInput> values) {
        var db = store.db;
        for (HistoryInput row : values) {
            String sql = "INSERT INTO daily_activity_history(UserID,DeviceID,ActivityKey,ActivityDate,Kind,SourceID,Title,Note,Progress,IsBookmarked) "
                    + "VALUES (?,?,?,?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE ActivityDate=VALUES(ActivityDate),Kind=VALUES(Kind),"
                    + "SourceID=VALUES(SourceID),Title=VALUES(Title),Note=VALUES(Note),Progress=VALUES(Progress),IsBookmarked=VALUES(IsBookmarked)";
            db.update(sql, user, device, row.key(), row.date(), row.kind(), row.sourceId(), row.title(),
                    defaultValue(row.note(), ""), row.progress(), row.bookmarked());
        }
    }

    private void removeAbsentHistory(int user, String device, List<HistoryInput> values) {
        Set<String> retained = new HashSet<>();
        for (HistoryInput row : values) retained.add(row.key());
        for (String key : store.db.queryForList(
                "SELECT ActivityKey FROM daily_activity_history WHERE UserID=? AND DeviceID=?", String.class, user, device)) {
            if (!retained.contains(key)) store.db.update(
                    "DELETE FROM daily_activity_history WHERE UserID=? AND DeviceID=? AND ActivityKey=?", user, device, key);
        }
    }

    private void removeAbsent(String device, String kind, Set<String> retained, java.util.function.IntConsumer remove) {
        for (var entry : store.records(device, kind).entrySet()) {
            if (retained.contains(entry.getKey())) continue;
            remove.accept(entry.getValue());
            store.unmap(device, kind, entry.getKey());
        }
    }

    private Integer resolveHabit(String localId, Map<String, Integer> known) {
        if (localId == null || localId.isBlank()) return null;
        Integer mapped = known.get(localId);
        if (mapped != null) return mapped;
        String candidate = localId.startsWith("api-habit-") ? localId.substring("api-habit-".length()) : localId;
        try {
            int id = Integer.parseInt(candidate);
            List<Integer> owned = store.db.queryForList(
                    "SELECT HabitID FROM habits WHERE HabitID=? AND UserID=?", Integer.class, id, store.user.id());
            return owned.isEmpty() ? null : owned.get(0);
        } catch (NumberFormatException ignored) { return null; }
    }

    private LocalDateTime reminderDateTime(String date, String time) {
        LocalDate day = date == null || date.isBlank() ? LocalDate.now() : LocalDate.parse(date);
        LocalTime at = LocalTime.parse(time.length() == 5 ? time + ":00" : time);
        return LocalDateTime.of(day, at);
    }
    private LocalTime parseTime(String time) {
        return time == null || time.isBlank() ? null : LocalTime.parse(time.length() == 5 ? time + ":00" : time);
    }
    private String emptyToNull(String value) { return value == null || value.isBlank() ? null : value; }
    private String defaultValue(String value, String fallback) { return value == null || value.isBlank() ? fallback : value; }
    private Set<String> ids(List<? extends Object> values) {
        Set<String> result = new HashSet<>();
        for (Object value : values) {
            if (value instanceof HabitInput h) result.add(h.id());
            else if (value instanceof JournalInput n) result.add(n.id());
            else if (value instanceof EventInput e) result.add(e.id());
            else if (value instanceof ReminderInput r) result.add(r.id());
        }
        return result;
    }
    private static <T> List<T> safe(List<T> values) { return values == null ? List.of() : values; }
    private static void unique(Collection<String> keys) {
        if (new HashSet<>(keys).size() != keys.size()) throw new IllegalArgumentException("Duplicate local ID");
    }
}
