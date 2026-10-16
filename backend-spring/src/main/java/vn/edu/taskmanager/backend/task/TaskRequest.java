package vn.edu.taskmanager.backend.task;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

public record TaskRequest(
        @NotNull Integer userId,
        @NotBlank @Size(max = 200) String title,
        String description,
        Task.Priority priority,
        Task.Status status,
        @Min(0) @Max(100) Integer progress,
        LocalDateTime startDate,
        LocalDateTime dueDate
) { }
