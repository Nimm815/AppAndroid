package vn.edu.taskmind.productivity.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import vn.edu.taskmind.productivity.domain.Reminder;
import vn.edu.taskmind.productivity.repository.CalendarEventRepository;
import vn.edu.taskmind.productivity.repository.ReminderRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@RestController
@Profile("dev")
@RequestMapping("/api/reminders")
public class ReminderController {
    private final ReminderRepository reminders;
    private final CalendarEventRepository events;
    private final org.springframework.web.client.RestClient taskService;
    private final CurrentUser user;

    public ReminderController(ReminderRepository reminders, CalendarEventRepository events,
                              @org.springframework.beans.factory.annotation.Value("${taskmind.task-url:http://127.0.0.1:8082}") String taskUrl,
                              CurrentUser user) {
        this.reminders = reminders;
        this.events = events;
        this.taskService = org.springframework.web.client.RestClient.builder().baseUrl(taskUrl).build();
        this.user = user;
    }

    public record Input(@NotBlank @Size(max=255) String title, Integer taskId, Integer eventId,
                        @NotNull LocalDateTime remindAt, @Size(max=100) String repeatRule,
                        Boolean enabled) { }

    @GetMapping
    public List<Reminder> all() {
        return reminders.findByUserId(user.id()).stream()
                .sorted(java.util.Comparator.comparing(Reminder::getRemindAt)).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Reminder create(@Valid @RequestBody Input body) { return save(null, body); }

    @PutMapping("/{id}")
    public Reminder update(@PathVariable int id, @Valid @RequestBody Input body) { return save(id, body); }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable int id) { reminders.delete(owned(id)); }

    private Reminder save(Integer id, Input body) {
        if ((body.taskId() == null) == (body.eventId() == null))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nhắc việc phải gắn với đúng một công việc hoặc sự kiện");
        if (body.eventId() != null && events.findById(body.eventId())
                .filter(event -> Objects.equals(event.getUserId(), user.id())).isEmpty())
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy sự kiện");
        if (body.taskId() != null) {
            try {
                String authorization = org.springframework.web.context.request.RequestContextHolder
                        .getRequestAttributes() instanceof org.springframework.web.context.request.ServletRequestAttributes attributes
                        ? attributes.getRequest().getHeader("Authorization") : null;
                taskService.get().uri("/api/tasks/{id}", body.taskId()).header("Authorization", authorization)
                        .retrieve().toEntity(Object.class);
            } catch (org.springframework.web.client.HttpClientErrorException.NotFound error) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy công việc");
            } catch (org.springframework.web.client.RestClientException error) {
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Task Service chưa phản hồi");
            }
        }
        Reminder reminder = id == null ? new Reminder() : owned(id);
        if (id == null) reminder.setUserId(user.id());
        reminder.setTitle(body.title().trim());
        reminder.setTaskId(body.taskId());
        reminder.setEventId(body.eventId());
        reminder.setRemindAt(body.remindAt());
        reminder.setRepeatRule(body.repeatRule());
        reminder.setIsEnabled(body.enabled() == null || body.enabled());
        return reminders.saveAndFlush(reminder);
    }

    private Reminder owned(int id) {
        return reminders.findById(id).filter(reminder -> Objects.equals(reminder.getUserId(), user.id()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy nhắc việc"));
    }
}
