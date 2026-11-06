package dev.platform.task.infrastructure.persistence.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import dev.platform.task.infrastructure.persistence.entity.CardEntity;

// Truy vấn bảng cards.
public interface CardJpaRepository extends JpaRepository<CardEntity, Long> {

    @Query("select c from CardEntity c where c.projectId = :projectId and c.boardId = :boardId "
            + "and (:columnId is null or c.columnId = :columnId) "
            + "and (:position is null or c.position < :position or (c.position = :position and c.id < :id)) "
            + "order by c.position desc, c.id desc")
    List<CardEntity> findPage(@Param("projectId") Long projectId, @Param("boardId") Long boardId,
            @Param("columnId") Long columnId, @Param("position") Integer position,
            @Param("id") Long id, Pageable pageable);

    List<CardEntity> findByProjectIdAndBoardIdOrderByPositionAsc(Long projectId, Long boardId);

    List<CardEntity> findByProjectIdAndBoardIdAndColumnIdOrderByPositionAsc(
            Long projectId, Long boardId, Long columnId);

    List<CardEntity> findByProjectIdAndBoardIdAndColumnIdOrderByPositionDescIdDesc(
            Long projectId, Long boardId, Long columnId);

    @Query("select max(c.position) from CardEntity c where c.projectId = :projectId and c.boardId = :boardId and c.columnId = :columnId")
    Integer maxPosition(@Param("projectId") Long projectId, @Param("boardId") Long boardId,
            @Param("columnId") Long columnId);

    Optional<CardEntity> findByIdAndProjectId(Long id, Long projectId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select card from CardEntity card where card.id = :id and card.projectId = :projectId")
    Optional<CardEntity> lockByIdAndProjectId(@Param("id") Long id, @Param("projectId") Long projectId);

}
