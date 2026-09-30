package dev.platform.task.api.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

// Body tạo thẻ (kanban-api.md 3.1).
public record CreateCardRequest(
        Long columnId,
        String title,
        String description,
        UUID assigneeId,
        OffsetDateTime dueDate
) {
}
