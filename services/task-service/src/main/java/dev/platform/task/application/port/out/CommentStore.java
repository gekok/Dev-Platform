package dev.platform.task.application.port.out;

import java.util.List;
import java.util.UUID;
import dev.platform.task.domain.aggregate.CardAggregate.CommentValue;

public interface CommentStore {
    Long requireBoardId(Long projectId, Long cardId);
    CommentValue save(Long projectId, Long cardId, UUID authorId, String content);
    List<CommentValue> findAll(Long projectId, Long cardId);
}
