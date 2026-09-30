package dev.platform.task.domain.repository;

import java.util.List;
import java.util.Optional;

import dev.platform.task.domain.aggregate.CardAggregate;

// Hợp đồng lưu trữ Card cho application dùng.
public interface CardRepository {

    CardAggregate save(CardAggregate card);

    Optional<CardAggregate> findById(Long id);

    List<CardAggregate> findByBoardIdOrderByPosition(Long boardId);

    List<CardAggregate> findByBoardIdAndColumnId(Long boardId, Long columnId);
}
