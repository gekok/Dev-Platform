package dev.platform.notification.api.dto;

import java.time.OffsetDateTime;

import dev.platform.notification.domain.aggregate.NotificationAggregate;

// Thông báo trả về (kanban-api.md 4.1).
public record NotificationResponse(
        Long id,
        String type,
        String title,
        Long cardId,
        Long boardId,
        boolean read,
        OffsetDateTime createdAt
) {

    // Map từ Aggregate sang Response.
    public static NotificationResponse from(NotificationAggregate n) {
        return new NotificationResponse(
                n.getId(),
                n.getType() == null ? null : n.getType().name(),
                n.getTitle(),
                n.getCardId(),
                n.getBoardId(),
                n.isRead(),
                n.getCreatedAt());
    }
}
