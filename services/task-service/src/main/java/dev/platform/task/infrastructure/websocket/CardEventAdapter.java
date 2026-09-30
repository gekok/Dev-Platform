package dev.platform.task.infrastructure.websocket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import dev.platform.task.application.port.out.CardEventPublisherPort;

// Adapter bắn event thẻ, kỳ này chỉ log stub.
@Component
public class CardEventAdapter implements CardEventPublisherPort {

    private static final Logger log = LoggerFactory.getLogger(CardEventAdapter.class);

    @Override
    public void cardCreated(Long cardId, Long boardId, Long columnId) {
        // TODO kỳ 1: bắn WS /ws/boards/{boardId} + gọi notification-service.
        log.info("card.created cardId={} boardId={} columnId={}", cardId, boardId, columnId);
    }

    @Override
    public void cardMoved(Long cardId, Long toColumnId, int toPosition) {
        // TODO kỳ 1: bắn WS /ws/boards/{boardId} event card.moved.
        log.info("card.moved cardId={} toColumnId={} toPosition={}", cardId, toColumnId, toPosition);
    }

    @Override
    public void commentAdded(Long cardId, Long commentId) {
        // TODO kỳ 1: bắn WS event comment.added + gọi notification-service.
        log.info("comment.added cardId={} commentId={}", cardId, commentId);
    }
}
