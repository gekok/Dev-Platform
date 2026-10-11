package dev.platform.task.todo.api;

import dev.platform.task.todo.domain.TodoTaskPriority;
import dev.platform.task.todo.domain.TodoTaskStatus;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;

public record UpdateTodoTaskRequest(
        @Size(max = 255) String title,
        String description,
        TodoTaskStatus status,
        TodoTaskPriority priority,
        OffsetDateTime startDate,
        OffsetDateTime deadline) {
}
