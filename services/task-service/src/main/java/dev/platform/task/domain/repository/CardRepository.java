package dev.platform.task.domain.repository;

import java.util.List;
import java.util.Optional;

import dev.platform.task.domain.aggregate.CardAggregate;

// Hợp đồng lưu trữ Card cho application dùng.
public interface CardRepository {

    CardAggregate save(CardAggregate card);

    Optional<CardAggregate> findById(Long projectId, Long id);

    List<CardAggregate> findByProjectIdAndBoardIdOrderByPosition(Long projectId, Long boardId);

    List<CardAggregate> findByProjectIdAndBoardIdAndColumnId(Long projectId, Long boardId, Long columnId);
}
