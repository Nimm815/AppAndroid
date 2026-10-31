package vn.edu.taskmind.productivity.api;

import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import vn.edu.taskmind.productivity.domain.Habit;
import vn.edu.taskmind.productivity.domain.HabitLog;
import vn.edu.taskmind.productivity.repository.HabitLogRepository;
import vn.edu.taskmind.productivity.repository.HabitRepository;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static vn.edu.taskmind.productivity.api.HabitDtos.*;

@Service
@Profile("dev")
@Transactional
public class HabitManagementService {
    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final String DEFAULT_ICON = "🌱";
    private static final String DEFAULT_COLOR = "#5B6FDE";
    // The existing Description column carries the weekday mask for CUSTOM schedules.
    private static final String WEEKDAYS_PREFIX = "[[TASKMIND_WEEKDAYS:";
    private final HabitRepository habits;
    private final HabitLogRepository logs;
    private final CurrentUser user;

    public HabitManagementService(HabitRepository habits, HabitLogRepository logs, CurrentUser user) {
        this.habits = habits;
        this.logs = logs;
        this.user = user;
    }

    private LocalDate today() { return LocalDate.now(ZONE); }

    private Habit owned(int id) {
        return habits.findOwned(id, user.id())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy thói quen"));
    }

    private boolean scheduled(Habit habit, LocalDate date) {
        if (date.isBefore(habit.getStartDate()) || (habit.getEndDate() != null && date.isAfter(habit.getEndDate()))) return false;
        return switch (habit.getFrequency()) {
            case "DAILY" -> true;
            case "WEEKLY" -> date.getDayOfWeek() == habit.getStartDate().getDayOfWeek();
            case "CUSTOM" -> (customWeekdays(habit) & (1 << (date.getDayOfWeek().getValue() - 1))) != 0;
            default -> false;
        };
    }

    private int customWeekdays(Habit habit) {
        String description = habit.getDescription();
        if (description == null || !description.startsWith(WEEKDAYS_PREFIX)) return 0;
        int end = description.indexOf("]]", WEEKDAYS_PREFIX.length());
        if (end < 0) return 0;
        try { return Integer.parseInt(description.substring(WEEKDAYS_PREFIX.length(), end)) & 127; }
        catch (NumberFormatException ignored) { return 0; }
    }

    private String userDescription(Habit habit) {
        String description = habit.getDescription();
        if (description == null || !description.startsWith(WEEKDAYS_PREFIX)) return description;
        int end = description.indexOf("]]", WEEKDAYS_PREFIX.length());
        if (end < 0) return description;
        String visible = description.substring(end + 2);
        return visible.startsWith("\n") ? visible.substring(1) : visible;
    }

    private String storedDescription(String description, int mask, boolean custom) {
        String visible = description == null ? "" : description;
        return custom ? WEEKDAYS_PREFIX + mask + "]]\n" + visible : visible;
    }

    private View view(Habit habit) {
        List<HabitLog> allLogs = logs.findByHabitIdAndLogDateBetween(habit.getHabitId(), habit.getStartDate(),
                habit.getEndDate() == null ? LocalDate.of(9999, 12, 31) : habit.getEndDate());
        Set<LocalDate> completed = new HashSet<>();
        Map<LocalDate, Integer> actualByDay = new HashMap<>();
        for (HabitLog log : allLogs) {
            actualByDay.put(log.getLogDate(), log.getActualCount());
            if ("COMPLETED".equals(log.getStatus())) completed.add(log.getLogDate());
        }
        LocalDate today = today();
        int streak = 0;
        LocalDate streakCursor = completed.contains(today) ? today : today.minusDays(1);
        for (LocalDate day = streakCursor; !day.isBefore(habit.getStartDate()); day = day.minusDays(1)) {
            if (!scheduled(habit, day)) continue;
            if (!completed.contains(day)) break;
            streak++;
        }
        int longest = 0, run = 0;
        LocalDate from = habit.getStartDate();
        LocalDate until = habit.getEndDate() == null || habit.getEndDate().isAfter(today()) ? today() : habit.getEndDate();
        if (!from.isAfter(until)) {
            for (LocalDate day = from; !day.isAfter(until); day = day.plusDays(1)) {
                if (!scheduled(habit, day)) continue;
                run = completed.contains(day) ? run + 1 : 0;
                longest = Math.max(longest, run);
            }
        }
        long days = !from.isAfter(until) ? ChronoUnit.DAYS.between(from, until) + 1 : 0;
        long scheduledDays = 0;
        if (!from.isAfter(until)) {
            for (LocalDate day = from; !day.isAfter(until); day = day.plusDays(1))
                if (scheduled(habit, day)) scheduledDays++;
        }
        long completedDays = completed.stream().filter(d -> !d.isAfter(until) && scheduled(habit, d)).count();
        double rate = scheduledDays == 0 ? 0 : completedDays * 100.0 / scheduledDays;
        int weekdayMask = switch (habit.getFrequency()) {
            case "DAILY" -> 127;
            case "CUSTOM" -> customWeekdays(habit);
            default -> 1 << (habit.getStartDate().getDayOfWeek().getValue() - 1);
        };
        int actualToday = actualByDay.getOrDefault(today, 0);
        return new View(habit.getHabitId(), habit.getHabitName(), userDescription(habit), DEFAULT_ICON, DEFAULT_COLOR,
                weekdayMask, habit.getStartDate(), true, completed.contains(today), actualToday, streak, longest,
                Math.toIntExact(completedDays), Math.toIntExact(scheduledDays), rate,
                habit.getTargetCount(), "lần", null, false);
    }

    public List<View> all() {
        return habits.findByUserIdAndIsActiveTrue(user.id()).stream()
                .map(this::view).toList();
    }

    public View get(int id) { return view(owned(id)); }

    public View save(Integer id, Input input) {
        if (input.startDate().isBefore(LocalDate.of(2000, 1, 1)))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ngày bắt đầu phải từ năm 2000 trở đi");
        if (input.targetUnit() != null && !input.targetUnit().isBlank() && !input.targetUnit().equals("lần"))
            throw unsupported("Đơn vị mục tiêu chưa có cột lưu trong bảng habits hiện tại.");
        if (Boolean.TRUE.equals(input.reminderEnabled()))
            throw unsupported("Bảng habits hiện tại không lưu giờ nhắc riêng cho thói quen.");

        int mask = input.weekdays();
        if (mask < 1 || mask > 127)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Chọn ít nhất một ngày trong tuần");
        String frequency = mask == 127 ? "DAILY" : Integer.bitCount(mask) == 1 ? "WEEKLY" : "CUSTOM";
        LocalDate storedStart = input.startDate();
        if (frequency.equals("WEEKLY")) {
            int selected = Integer.numberOfTrailingZeros(mask) + 1;
            int delta = Math.floorMod(selected - storedStart.getDayOfWeek().getValue(), 7);
            storedStart = storedStart.plusDays(delta);
        }

        Habit habit = id == null ? new Habit() : owned(id);
        if (id == null) habit.setUserId(user.id());
        else {
            if (!habit.getFrequency().equals(frequency) || !habit.getStartDate().equals(storedStart)
                    || (frequency.equals("CUSTOM") && customWeekdays(habit) != mask))
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Không đổi lịch hoặc ngày bắt đầu để giữ nguyên lịch sử đã ghi.");
            if (input.endDate() != null && !input.endDate().equals(habit.getEndDate())
                    && !logs.findByHabitIdAndLogDateBetween(id, LocalDate.of(1000, 1, 1), LocalDate.of(9999, 12, 31)).isEmpty())
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Không đổi ngày kết thúc sau khi đã có lịch sử ghi nhận.");
        }
        int target = input.targetCount() == null ? 1 : input.targetCount();
        if (target < 1) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mục tiêu phải lớn hơn 0");
        if (id != null && logs.findByHabitIdAndLogDateBetween(id, LocalDate.of(1000, 1, 1), LocalDate.of(9999, 12, 31)).stream()
                .anyMatch(log -> log.getActualCount() != null && log.getActualCount() > target))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Mục tiêu mới thấp hơn dữ liệu đã ghi nhận");

        habit.setHabitName(input.name().trim());
        habit.setDescription(storedDescription(input.description(), mask, frequency.equals("CUSTOM")));
        habit.setFrequency(frequency);
        habit.setStartDate(storedStart);
        habit.setEndDate(input.endDate());
        if (habit.getEndDate() != null && habit.getEndDate().isBefore(storedStart))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ngày kết thúc phải sau ngày bắt đầu");
        habit.setTargetCount(target);
        habit.setIsActive(true);
        habits.saveAndFlush(habit);
        return view(habit);
    }

    public void delete(int id) { owned(id).setIsActive(false); }

    public View check(int id, LocalDate date, Boolean completed, Integer requestedCount, String note) {
        Habit habit = owned(id);
        int count = requestedCount == null ? (Boolean.TRUE.equals(completed) ? habit.getTargetCount() : 0) : requestedCount;
        if (date.isAfter(today()) || !scheduled(habit, date))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ngày không thuộc lịch thực hiện hoặc ở tương lai");
        if (count < 0 || count > habit.getTargetCount())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Giá trị thực hiện phải từ 0 đến mục tiêu");
        HabitLog log = logs.findByHabitIdAndLogDate(id, date).orElseGet(HabitLog::new);
        log.setHabitId(id);
        log.setLogDate(date);
        log.setActualCount(count);
        log.setStatus(count == 0 ? "MISSED" : count >= habit.getTargetCount() ? "COMPLETED" : "PARTIAL");
        log.setNote(note);
        if (log.getCreatedAt() == null) log.setCreatedAt(LocalDateTime.now(ZoneOffset.UTC));
        log.setUpdatedAt(LocalDateTime.now(ZoneOffset.UTC));
        logs.saveAndFlush(log);
        return view(habit);
    }

    public List<Day> history(int id, LocalDate from, LocalDate to) {
        Habit habit = owned(id);
        if (to.isBefore(from) || ChronoUnit.DAYS.between(from, to) > 365)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Khoảng xem lịch tối đa 366 ngày");
        Map<LocalDate, HabitLog> entries = new HashMap<>();
        for (HabitLog log : logs.findByHabitIdAndLogDateBetween(id, from, to)) entries.put(log.getLogDate(), log);
        List<Day> days = new ArrayList<>();
        for (LocalDate day = from; !day.isAfter(to); day = day.plusDays(1)) {
            HabitLog log = entries.get(day);
            days.add(new Day(day, scheduled(habit, day), log != null && "COMPLETED".equals(log.getStatus()),
                    log == null ? 0 : log.getActualCount()));
        }
        return days;
    }

    private ResponseStatusException unsupported(String message) {
        return new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, message);
    }
}
