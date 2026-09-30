package dev.platform.task.api.dto;

// Body kéo thẻ (kanban-api.md 3.3).
public record MoveCardRequest(
        Long toColumnId,
        int toPosition
) {
}
