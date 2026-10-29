package dev.platform.task.infrastructure.persistence.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import dev.platform.task.infrastructure.persistence.entity.CardEntity;

// Truy vấn bảng cards.
public interface CardJpaRepository extends JpaRepository<CardEntity, Long> {

    List<CardEntity> findByProjectIdAndBoardIdOrderByPositionAsc(Long projectId, Long boardId);

    List<CardEntity> findByProjectIdAndBoardIdAndColumnIdOrderByPositionAsc(
            Long projectId, Long boardId, Long columnId);

    Optional<CardEntity> findByIdAndProjectId(Long id, Long projectId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select card from CardEntity card where card.id = :id and card.projectId = :projectId")
    Optional<CardEntity> lockByIdAndProjectId(@Param("id") Long id, @Param("projectId") Long projectId);

}
