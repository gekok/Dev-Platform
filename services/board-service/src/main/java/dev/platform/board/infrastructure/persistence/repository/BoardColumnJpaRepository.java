package dev.platform.board.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import dev.platform.board.infrastructure.persistence.entity.BoardColumnEntity;

// Truy vấn bảng board_columns.
public interface BoardColumnJpaRepository extends JpaRepository<BoardColumnEntity, Long> {
}
