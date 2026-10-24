package dev.platform.task.todo.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;

public record CreateTodoTaskRequest(
        @NotBlank @Size(max = 255) String title,
        String description,
        Long projectId,
        OffsetDateTime startDate,
        OffsetDateTime deadline) {
}
