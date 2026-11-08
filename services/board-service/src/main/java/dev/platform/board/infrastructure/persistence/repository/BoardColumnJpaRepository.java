package dev.platform.board.infrastructure.persistence.repository;

import dev.platform.board.infrastructure.persistence.entity.BoardColumnEntity;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;

import java.util.List;
import java.util.Optional;

public interface BoardColumnJpaRepository extends JpaRepository<BoardColumnEntity, Long> {
    List<BoardColumnEntity> findByBoard_IdOrderByPositionAsc(Long boardId);

    Optional<BoardColumnEntity> findByIdAndBoard_Id(Long columnId, Long boardId);

    long countByBoard_Id(Long boardId);

    @Modifying
    @Query("update BoardColumnEntity item set item.position = item.position + 1000000 "
            + "where item.board.id = :boardId")
    int moveAllToTemporaryPositions(@Param("boardId") Long boardId);

    @Modifying
    @Query("update BoardColumnEntity item set item.position = :position "
            + "where item.id = :columnId and item.board.id = :boardId")
    int setPosition(
            @Param("boardId") Long boardId,
            @Param("columnId") Long columnId,
            @Param("position") int position);

}
