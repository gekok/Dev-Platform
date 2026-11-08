package dev.platform.task.application.port.out;

import dev.platform.task.domain.aggregate.CardAggregate;

public interface MoveCardStore {
    CardAggregate find(Long projectId, Long cardId);
    void lockBoard(Long projectId, Long boardId);
    int nextPosition(Long projectId, Long boardId, Long columnId);
    CardAggregate reload(Long projectId, Long cardId);
    CardAggregate reorder(Long projectId, Long boardId, Long cardId, Long sourceColumnId,
            Long destinationColumnId, int targetIndex);
}
