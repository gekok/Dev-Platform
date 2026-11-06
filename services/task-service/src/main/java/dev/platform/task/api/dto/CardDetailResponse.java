package dev.platform.task.api.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

import dev.platform.task.domain.aggregate.CardAggregate;

public record CardDetailResponse(
        Long id,
        Long projectId,
        Long boardId,
        Long columnId,
        String title,
        String description,
        String status,
        int position,
        UUID assigneeId,
        OffsetDateTime dueDate
) {

    public static CardDetailResponse from(CardAggregate card) {
        return new CardDetailResponse(
                card.getId(),
                card.getProjectId(),
                card.getBoardId(),
                card.getColumnId(),
                card.getTitle(),
                card.getDescription(),
                card.getStatus() == null ? null : card.getStatus().name(),
                card.getPosition(),
                card.getAssigneeId(),
                card.getDueDate());
    }
}
