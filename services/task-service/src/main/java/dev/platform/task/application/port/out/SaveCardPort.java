package dev.platform.task.application.port.out;

import dev.platform.task.domain.repository.CardRepository;
import dev.platform.task.domain.aggregate.CardAggregate;
import java.util.Optional;

// Cổng ra: lưu Card (adapter JPA cài đặt).
public interface SaveCardPort extends CardRepository {
    Optional<CardAggregate> findForUpdate(Long projectId, Long cardId);
}
