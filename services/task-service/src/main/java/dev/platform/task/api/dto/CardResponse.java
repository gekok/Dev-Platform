package dev.platform.task.api.dto;

import java.util.UUID;

import dev.platform.task.domain.aggregate.CardAggregate;

// Thẻ trả về (kanban-api.md 3.1).
public record CardResponse(
        Long id,
        Long projectId,
        Long boardId,
        Long columnId,
        String title,
        String status,
        int position,
        UUID assigneeId
) {

    // Map từ Aggregate sang Response.
    public static CardResponse from(CardAggregate card) {
        return new CardResponse(
                card.getId(),
                card.getProjectId(),
                card.getBoardId(),
                card.getColumnId(),
                card.getTitle(),
                card.getStatus() == null ? null : card.getStatus().name(),
                card.getPosition(),
                card.getAssigneeId());
    }
}
