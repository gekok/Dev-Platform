package dev.platform.task.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import dev.platform.task.infrastructure.persistence.entity.CardCommentEntity;

// Truy vấn bảng card_comments.
public interface CardCommentJpaRepository extends JpaRepository<CardCommentEntity, Long> {
}
