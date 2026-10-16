package vn.edu.taskmanager.backend.task;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class TaskService {
    private final TaskRepository repository;

    public TaskService(TaskRepository repository) {
        this.repository = repository;
    }

    public List<Task> findAll(Integer userId) {
        return repository.findAllByUserIdOrderByDueDateAsc(userId);
    }

    public Task findOne(Integer userId, Integer taskId) {
        return repository.findById(taskId)
                .filter(task -> task.getUserId().equals(userId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy công việc"));
    }

    public Task create(TaskRequest request) {
        Task task = new Task();
        apply(task, request);
        return repository.save(task);
    }

    public Task update(Integer userId, Integer taskId, TaskRequest request) {
        Task task = findOne(userId, taskId);
        if (!userId.equals(request.userId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "userId trong đường dẫn và nội dung phải giống nhau");
        }
        apply(task, request);
        return repository.save(task);
    }

    public void delete(Integer userId, Integer taskId) {
        repository.delete(findOne(userId, taskId));
    }

    private void apply(Task task, TaskRequest request) {
        task.setUserId(request.userId());
        task.setTitle(request.title().trim());
        task.setDescription(request.description());
        task.setPriority(request.priority() == null ? Task.Priority.MEDIUM : request.priority());
        task.setStatus(request.status() == null ? Task.Status.TODO : request.status());
        task.setProgress(request.progress() == null ? 0 : request.progress());
        task.setStartDate(request.startDate());
        task.setDueDate(request.dueDate());
        task.setCompletedAt(task.getStatus() == Task.Status.COMPLETED ? LocalDateTime.now() : null);
    }
}
