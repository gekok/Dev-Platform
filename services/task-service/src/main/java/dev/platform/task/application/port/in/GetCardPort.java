package dev.platform.task.application.port.in;

import dev.platform.task.domain.aggregate.CardAggregate;

public interface GetCardPort {

    CardAggregate getById(Long projectId, Long cardId);
}
