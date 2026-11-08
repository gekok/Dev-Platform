package dev.platform.task.infrastructure.websocket;

import java.util.Map;

import dev.platform.task.application.port.out.CardEventPublisherPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Publishes ephemeral Board updates only after the owning SQL transaction commits. */
@Slf4j
@Component
@RequiredArgsConstructor
public class CardEventAdapter implements CardEventPublisherPort {

    private final ObjectProvider<BoardWebSocketHandler> handlers;

    @Override
    public void cardCreated(Long cardId, Long boardId, Long columnId) {
        publishAfterCommit(boardId, Map.of("event", "card.created", "cardId", cardId, "columnId", columnId));
    }

    @Override
    public void cardMoved(Long cardId, Long boardId, Long toColumnId, int toPosition) {
        publishAfterCommit(boardId, Map.of(
                "event", "card.moved", "cardId", cardId, "toColumnId", toColumnId, "toPosition", toPosition));
    }

    @Override
    public void commentAdded(Long cardId, Long boardId, Long commentId) {
        publishAfterCommit(boardId, Map.of("event", "comment.added", "cardId", cardId, "commentId", commentId));
    }

    private void publishAfterCommit(Long boardId, Object payload) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()
                || !TransactionSynchronizationManager.isActualTransactionActive()) {
            log.debug("Dropping Board event outside a committed transaction for board {}", boardId);
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    BoardWebSocketHandler handler = handlers.getIfAvailable();
                    if (handler == null) {
                        return;
                    }
                    handler.broadcast(boardId, payload);
                } catch (RuntimeException exception) {
                    log.debug("Best-effort Board event fanout failed for board {}", boardId, exception);
                }
            }
        });
    }
}
