package dev.platform.board.infrastructure.adapter;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

import dev.platform.board.application.port.out.SaveBoardPort;
import dev.platform.board.domain.aggregate.BoardAggregate;
import dev.platform.board.domain.aggregate.BoardAggregate.BoardColumnValue;
import dev.platform.board.infrastructure.persistence.entity.BoardColumnEntity;
import dev.platform.board.infrastructure.persistence.entity.BoardEntity;
import dev.platform.board.infrastructure.persistence.repository.BoardJpaRepository;
import lombok.RequiredArgsConstructor;

// Adapter JPA cho cổng ra SaveBoardPort.
@Component
@RequiredArgsConstructor
public class SaveBoardAdapter implements SaveBoardPort {

    private final BoardJpaRepository boardJpaRepository;

    @Override
    public BoardAggregate save(BoardAggregate board) {
        return toAggregate(boardJpaRepository.save(toEntity(board)));
    }

    @Override
    public Optional<BoardAggregate> findById(Long id) {
        return boardJpaRepository.findById(id).map(this::toAggregate);
    }

    @Override
    public List<BoardAggregate> findByOwnerId(UUID ownerId) {
        return boardJpaRepository.findByOwnerId(ownerId).stream()
                .map(this::toAggregate)
                .toList();
    }

    @Override
    public Optional<BoardAggregate> findByKey(String key) {
        return boardJpaRepository.findByBoardKey(key).map(this::toAggregate);
    }

    // Chuyển Aggregate sang Entity để lưu.
    private BoardEntity toEntity(BoardAggregate board) {
        BoardEntity entity = BoardEntity.builder()
                .id(board.getId())
                .name(board.getName())
                .boardKey(board.getKey())
                .ownerId(board.getOwnerId())
                .columns(new ArrayList<>())
                .build();
        if (board.getColumns() != null) {
            for (BoardColumnValue c : board.getColumns()) {
                entity.getColumns().add(BoardColumnEntity.builder()
                        .id(c.id())
                        .board(entity)
                        .name(c.name())
                        .position(c.position())
                        .build());
            }
        }
        return entity;
    }

    // Chuyển Entity sang Aggregate để trả về.
    private BoardAggregate toAggregate(BoardEntity entity) {
        List<BoardColumnValue> columns = entity.getColumns() == null ? List.of()
                : entity.getColumns().stream()
                        .map(c -> new BoardColumnValue(c.getId(), c.getName(), c.getPosition()))
                        .toList();
        return new BoardAggregate(
                entity.getId(),
                entity.getName(),
                entity.getBoardKey(),
                entity.getOwnerId(),
                new ArrayList<>(columns));
    }
}
