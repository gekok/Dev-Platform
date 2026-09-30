package dev.platform.board.infrastructure.persistence.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import dev.platform.board.infrastructure.persistence.entity.BoardEntity;

// Truy vấn bảng boards.
public interface BoardJpaRepository extends JpaRepository<BoardEntity, Long> {

    List<BoardEntity> findByOwnerId(UUID ownerId);

    Optional<BoardEntity> findByBoardKey(String boardKey);
}
