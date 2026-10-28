package dev.platform.board.deletion.persistence.repository;

import dev.platform.board.deletion.persistence.entity.ProjectDeletionOperationEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ProjectDeletionOperationJpaRepository extends JpaRepository<ProjectDeletionOperationEntity, UUID> {
}
