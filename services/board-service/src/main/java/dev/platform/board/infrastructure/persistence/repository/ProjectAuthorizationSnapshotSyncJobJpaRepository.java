package dev.platform.board.infrastructure.persistence.repository;

import dev.platform.board.infrastructure.persistence.entity.ProjectAuthorizationSnapshotSyncJobEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProjectAuthorizationSnapshotSyncJobJpaRepository
        extends JpaRepository<ProjectAuthorizationSnapshotSyncJobEntity, UUID> {

    Optional<ProjectAuthorizationSnapshotSyncJobEntity> findByOperationId(UUID operationId);

    List<ProjectAuthorizationSnapshotSyncJobEntity> findTop50ByStatusAndNextAttemptAtLessThanEqualOrderByNextAttemptAtAsc(
            String status, OffsetDateTime now);
}
