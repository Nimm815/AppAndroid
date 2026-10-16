package vn.edu.taskmanager.backend.task;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users/{userId}/tasks")
public class TaskController {
    private final TaskService service;

    public TaskController(TaskService service) {
        this.service = service;
    }

    @GetMapping
    public List<Task> findAll(@PathVariable Integer userId) {
        return service.findAll(userId);
    }

    @GetMapping("/{taskId}")
    public Task findOne(@PathVariable Integer userId, @PathVariable Integer taskId) {
        return service.findOne(userId, taskId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Task create(@PathVariable Integer userId, @Valid @RequestBody TaskRequest request) {
        if (!userId.equals(request.userId())) {
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.BAD_REQUEST, "userId không khớp");
        }
        return service.create(request);
    }

    @PutMapping("/{taskId}")
    public Task update(@PathVariable Integer userId, @PathVariable Integer taskId, @Valid @RequestBody TaskRequest request) {
        return service.update(userId, taskId, request);
    }

    @DeleteMapping("/{taskId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Integer userId, @PathVariable Integer taskId) {
        service.delete(userId, taskId);
    }
}
