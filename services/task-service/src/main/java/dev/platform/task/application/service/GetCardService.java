package dev.platform.task.application.service;

import dev.platform.task.application.exception.CardNotFoundException;
import dev.platform.task.application.port.in.GetCardPort;
import dev.platform.task.application.port.out.SaveCardPort;
import dev.platform.task.domain.aggregate.CardAggregate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GetCardService implements GetCardPort {

    private final SaveCardPort cards;

    @Override
    @Transactional(readOnly = true)
    public CardAggregate getById(Long projectId, Long cardId) {
        if (projectId == null || cardId == null) {
            throw new IllegalArgumentException("Project and card are required");
        }
        return cards.findById(projectId, cardId).orElseThrow(CardNotFoundException::new);
    }
}
