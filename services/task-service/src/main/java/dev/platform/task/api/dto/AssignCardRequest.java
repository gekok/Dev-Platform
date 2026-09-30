package dev.platform.task.api.dto;

import java.util.UUID;

// Body gán người làm (kanban-api.md 3.4).
public record AssignCardRequest(
        UUID assigneeId
) {
}
