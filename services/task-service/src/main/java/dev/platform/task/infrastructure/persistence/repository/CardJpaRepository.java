package dev.platform.task.infrastructure.persistence.repository;

import java.util.List;
import java.util.Collection;

import org.springframework.data.jpa.repository.JpaRepository;

import dev.platform.task.infrastructure.persistence.entity.CardEntity;

// Truy vấn bảng cards.
public interface CardJpaRepository extends JpaRepository<CardEntity, Long> {

    List<CardEntity> findByBoardIdOrderByPositionAsc(Long boardId);

    List<CardEntity> findByBoardIdAndColumnIdOrderByPositionAsc(Long boardId, Long columnId);

    List<CardEntity> findTop100ByBoardIdInAndIdGreaterThanOrderByIdAsc(Collection<Long> boardIds, Long afterId);

    List<CardEntity> findTop100ByBoardIdInAndAssigneeIdAndIdGreaterThanOrderByIdAsc(
            Collection<Long> boardIds,
            java.util.UUID assigneeId,
            Long afterId);
}
