package dev.platform.task.application.port.out;

// Cổng ra: bắn event thẻ (kỳ 1 nối WebSocket + notification).
public interface CardEventPublisherPort {

    // Bắn event card.created cho cột.
    void cardCreated(Long cardId, Long boardId, Long columnId);

    // Bắn event card.moved về cột + vị trí mới.
    void cardMoved(Long cardId, Long toColumnId, int toPosition);

    // Bắn event comment.added cho thẻ.
    void commentAdded(Long cardId, Long commentId);
}
