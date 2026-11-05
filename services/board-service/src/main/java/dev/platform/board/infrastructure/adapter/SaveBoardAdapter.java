package dev.platform.board.infrastructure.adapter;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.ArrayList;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import dev.platform.board.application.port.out.SaveBoardPort;
import dev.platform.board.domain.aggregate.BoardAggregate;
import dev.platform.board.domain.aggregate.BoardAggregate.BoardColumnValue;
import dev.platform.board.infrastructure.persistence.entity.BoardEntity;
import dev.platform.board.infrastructure.persistence.repository.BoardJpaRepository;
import lombok.RequiredArgsConstructor;

// Board-first commands lack the Project context required by the Project-first schema.
@Component
@RequiredArgsConstructor
public class SaveBoardAdapter implements SaveBoardPort {
    private final BoardJpaRepository boardJpaRepository;

    private UnsupportedOperationException projectContextRequired() {
        return new UnsupportedOperationException("Board-first API is unavailable until Project-scoped use cases are implemented");
    }

    @Override
    public BoardAggregate save(BoardAggregate board) {
        throw projectContextRequired();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<BoardAggregate> findById(Long id) {
        return boardJpaRepository.findWithOwnerById(id).map(this::toAggregate);
    }

    @Override
    @Transactional(readOnly = true)
    public List<BoardAggregate> findByOwnerId(UUID ownerId) {
        return boardJpaRepository.findWithOwnerByOwnerId(ownerId).stream()
                .map(this::toAggregate)
                .toList();
    }

    @Override
    public Optional<BoardAggregate> findByKey(String key) {
        throw projectContextRequired();
    }

    private BoardAggregate toAggregate(Object[] row) {
        BoardEntity entity = (BoardEntity) row[0];
        UUID ownerId = (UUID) row[1];
        List<BoardColumnValue> columns = entity.getColumns().stream()
                .map(c -> new BoardColumnValue(c.getId(), c.getName(), c.getPosition()))
                .toList();
        return new BoardAggregate(entity.getId(), entity.getName(), entity.getBoardKey(),
                ownerId, new ArrayList<>(columns));
    }
}
