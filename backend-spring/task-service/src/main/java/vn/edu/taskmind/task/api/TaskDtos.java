package vn.edu.taskmind.task.api;

import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
public final class TaskDtos {
    public record ListInput(@NotBlank @Size(max=100) String name, @Pattern(regexp="#[0-9a-fA-F]{6}") String color) {}
    public record ListView(int id, String name, String color, long total, long completed) {}
    public record TaskInput(@NotBlank @Size(max=200) String title, @Size(max=10000) String description,
        @Size(max=10000) String notes, LocalDateTime startDate, LocalDateTime dueDate,
        @NotNull @Pattern(regexp="LOW|MEDIUM|HIGH|URGENT") String priority, @Positive Integer listId) {}
    public record Completion(@NotNull Boolean completed) {}
    public record Progress(@Min(0) @Max(100) int progress) {}
    public record Move(@NotNull @Positive Integer listId) {}
    public record TaskView(int id, String title, String description, String notes, LocalDateTime startDate,
        LocalDateTime dueDate, String priority, String status, int progress, Integer listId) {}
}
