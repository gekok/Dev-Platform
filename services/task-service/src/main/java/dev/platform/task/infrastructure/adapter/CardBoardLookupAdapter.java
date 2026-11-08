package dev.platform.task.infrastructure.adapter;

import dev.platform.task.application.port.out.CardBoardLookupPort;
import dev.platform.task.infrastructure.persistence.repository.CardJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class CardBoardLookupAdapter implements CardBoardLookupPort {
    private final CardJpaRepository cards;

    @Override
    public Optional<Long> findBoardId(Long projectId, Long cardId) {
        return cards.findByIdAndProjectId(cardId, projectId).map(card -> card.getBoardId());
    }
}
