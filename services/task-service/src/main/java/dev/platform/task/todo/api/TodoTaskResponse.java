package dev.platform.task.todo.api;

import dev.platform.task.todo.domain.TodoTaskPriority;
import dev.platform.task.todo.domain.TodoTaskStatus;
import dev.platform.task.todo.infrastructure.TodoTaskEntity;

import java.time.OffsetDateTime;
import java.util.UUID;

public record TodoTaskResponse(
        Long id,
        UUID ownerId,
        Long projectId,
        String title,
        String description,
        TodoTaskStatus status,
        TodoTaskPriority priority,
        OffsetDateTime startDate,
        OffsetDateTime deadline,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {

    public static TodoTaskResponse from(TodoTaskEntity task) {
        return new TodoTaskResponse(
                task.getId(), task.getOwnerId(), task.getProjectId(), task.getTitle(),
                task.getDescription(), task.getStatus(), task.getPriority(), task.getStartDate(),
                task.getDeadline(), task.getCreatedAt(), task.getUpdatedAt());
    }
}
