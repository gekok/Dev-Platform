package dev.platform.task.api.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

import dev.platform.task.domain.aggregate.CardAggregate.CommentValue;

// Bình luận trả về (kanban-api.md 3.5).
public record CommentResponse(
        Long id,
        Long cardId,
        UUID authorId,
        String content,
        OffsetDateTime createdAt
) {

    // Map từ CommentValue sang Response.
    public static CommentResponse from(Long cardId, CommentValue comment) {
        return new CommentResponse(
                comment.id(),
                cardId,
                comment.authorId(),
                comment.content(),
                comment.createdAt());
    }
}
