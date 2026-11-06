package dev.platform.task.application.port.out;

// Publishes ephemeral Board events to local WebSocket subscribers.
public interface CardEventPublisherPort {

    // Publishes card.created for the Board containing the column.
    void cardCreated(Long cardId, Long boardId, Long columnId);

    // Publishes card.moved with the destination column and position.
    void cardMoved(Long cardId, Long boardId, Long toColumnId, int toPosition);

    // Publishes comment.added without comment content.
    void commentAdded(Long cardId, Long boardId, Long commentId);
}
