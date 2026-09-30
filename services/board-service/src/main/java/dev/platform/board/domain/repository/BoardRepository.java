package dev.platform.board.domain.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import dev.platform.board.domain.aggregate.BoardAggregate;

// Hợp đồng lưu trữ Board cho application dùng.
public interface BoardRepository {

    BoardAggregate save(BoardAggregate board);

    Optional<BoardAggregate> findById(Long id);

    List<BoardAggregate> findByOwnerId(UUID ownerId);

    Optional<BoardAggregate> findByKey(String key);
}
