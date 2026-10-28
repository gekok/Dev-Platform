package dev.platform.task.deletion.persistence.repository;

import dev.platform.task.deletion.persistence.entity.TaskDeletionJobEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface TaskDeletionJobJpaRepository extends JpaRepository<TaskDeletionJobEntity, UUID> {

    Optional<TaskDeletionJobEntity> findByDeduplicationKey(String deduplicationKey);
}
