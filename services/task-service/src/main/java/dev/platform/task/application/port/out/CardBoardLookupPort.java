package dev.platform.task.application.port.out;

import java.util.Optional;

public interface CardBoardLookupPort {
    Optional<Long> findBoardId(Long projectId, Long cardId);
}
