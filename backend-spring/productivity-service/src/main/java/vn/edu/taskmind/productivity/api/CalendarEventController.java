package vn.edu.taskmind.productivity.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import vn.edu.taskmind.productivity.domain.CalendarEvent;
import vn.edu.taskmind.productivity.domain.Reminder;
import vn.edu.taskmind.productivity.repository.ReminderRepository;
import vn.edu.taskmind.productivity.repository.CalendarEventRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@RestController
@Profile("dev")
@RequestMapping("/api/calendar-events")
@Transactional
public class CalendarEventController {
    private final CalendarEventRepository events;
    private final ReminderRepository reminders;
    private final CurrentUser user;

    public CalendarEventController(CalendarEventRepository events, ReminderRepository reminders, CurrentUser user) {
        this.events = events;
        this.reminders = reminders;
        this.user = user;
    }

    public record Input(@NotBlank @Size(max=200) String title, String description,
                        @NotNull LocalDateTime startTime, @NotNull LocalDateTime endTime,
                        @Size(max=255) String location, Boolean allDay, @Size(max=20) String color,
                        Boolean createReminder, LocalDateTime remindAt) { }

    @GetMapping
    public List<CalendarEvent> all() {
        return events.findByUserId(user.id()).stream()
                .sorted(java.util.Comparator.comparing(CalendarEvent::getStartTime)).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CalendarEvent create(@Valid @RequestBody Input body) { return save(null, body); }

    @PutMapping("/{id}")
    public CalendarEvent update(@PathVariable int id, @Valid @RequestBody Input body) { return save(id, body); }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable int id) {
        CalendarEvent event = owned(id);
        if (reminders.findByUserId(user.id()).stream().anyMatch(reminder -> Objects.equals(id, reminder.getEventId())))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Sự kiện còn nhắc việc. Hãy xóa nhắc việc trước.");
        events.delete(event);
    }

    private CalendarEvent save(Integer id, Input body) {
        if (!body.endTime().isAfter(body.startTime()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Thời điểm kết thúc phải sau thời điểm bắt đầu");
        CalendarEvent event = id == null ? new CalendarEvent() : owned(id);
        if (id == null) event.setUserId(user.id());
        event.setTitle(body.title().trim());
        event.setDescription(body.description());
        event.setStartTime(body.startTime());
        event.setEndTime(body.endTime());
        event.setLocation(body.location());
        event.setAllDay(Boolean.TRUE.equals(body.allDay()));
        event.setColor(body.color());
        if (id == null && Boolean.TRUE.equals(body.createReminder()) && body.remindAt() == null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Thiếu thời điểm nhắc");
        CalendarEvent saved = events.saveAndFlush(event);
        if (id == null && Boolean.TRUE.equals(body.createReminder())) {
            Reminder reminder = new Reminder();
            reminder.setUserId(user.id());
            reminder.setEventId(saved.getEventId());
            reminder.setTitle(saved.getTitle());
            reminder.setRemindAt(body.remindAt());
            reminder.setIsEnabled(true);
            reminders.saveAndFlush(reminder);
        }
        return saved;
    }

    private CalendarEvent owned(int id) {
        return events.findById(id).filter(event -> Objects.equals(event.getUserId(), user.id()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy sự kiện"));
    }
}
