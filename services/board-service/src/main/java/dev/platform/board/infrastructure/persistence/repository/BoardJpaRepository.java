package dev.platform.board.infrastructure.persistence.repository;

import dev.platform.board.infrastructure.persistence.entity.BoardEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.List;

public interface BoardJpaRepository extends JpaRepository<BoardEntity, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select board from BoardEntity board where board.id = :boardId")
    Optional<BoardEntity> findForUpdate(@Param("boardId") Long boardId);

    @Query("""
            select board from BoardEntity board
            where board.projectEntity.id = :projectId
              and (:status is null or board.status = :status)
            """)
    Page<BoardEntity> findByProjectAndStatus(
            @Param("projectId") Long projectId,
            @Param("status") String status,
            Pageable pageable);

    boolean existsByProjectEntity_IdAndBoardKey(Long projectId, String boardKey);

    @Query("select board.id from BoardEntity board where board.projectEntity.id = :projectId")
    List<Long> findIdsByProjectId(@Param("projectId") Long projectId);
}
