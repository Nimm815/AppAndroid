package vn.edu.taskmind.productivity.api;

import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDateTime;
import vn.edu.taskmind.productivity.domain.Notification;
import vn.edu.taskmind.productivity.repository.NotificationRepository;

import java.util.List;
import java.util.Objects;

@RestController
@Profile("dev")
@RequestMapping("/api/notifications")
public class NotificationController {
    private final NotificationRepository notifications;
    private final CurrentUser user;

    public NotificationController(NotificationRepository notifications, CurrentUser user) {
        this.notifications = notifications;
        this.user = user;
    }

    @GetMapping
    public List<Notification> all() { return notifications.findByUserIdOrderByCreatedAtDesc(user.id()); }

    @PatchMapping("/{id}/read")
    public Notification markRead(@PathVariable int id, @org.springframework.web.bind.annotation.RequestBody(required = false) ReadRequest body) {
        Notification notification = notifications.findById(id)
                .filter(value -> Objects.equals(value.getUserId(), user.id()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy thông báo"));
        boolean read = body == null || body.read();
        notification.setIsRead(read);
        notification.setReadAt(read ? LocalDateTime.now() : null);
        return notifications.saveAndFlush(notification);
    }

    public record ReadRequest(boolean read) { }
}
