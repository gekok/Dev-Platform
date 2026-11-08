package dev.platform.task.infrastructure.adapter;

import dev.platform.task.application.port.out.ColumnTaskCountPort;
import dev.platform.task.infrastructure.persistence.repository.CardJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ColumnTaskCountAdapter implements ColumnTaskCountPort {
    private final CardJpaRepository cards;

    @Override
    public long countByColumnId(Long columnId) {
        return cards.countByColumnId(columnId);
    }
}
