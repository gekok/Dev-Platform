package dev.platform.task.infrastructure.persistence.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import dev.platform.task.infrastructure.persistence.entity.CardCommentEntity;

// Truy vấn bảng card_comments.
public interface CardCommentJpaRepository extends JpaRepository<CardCommentEntity, Long> {

    List<CardCommentEntity> findTop100ByCard_BoardIdInAndIdGreaterThanOrderByIdAsc(
            Collection<Long> boardIds,
            Long afterId);
}
