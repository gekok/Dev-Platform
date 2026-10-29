package dev.platform.user.infrastructure.persistence.repository;

import dev.platform.user.infrastructure.persistence.entity.AccountAuthorizationSnapshotSyncJobEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface AccountAuthorizationSnapshotSyncJobJpaRepository
        extends JpaRepository<AccountAuthorizationSnapshotSyncJobEntity, UUID> {

    List<AccountAuthorizationSnapshotSyncJobEntity> findTop50ByStatusAndNextAttemptAtLessThanEqualOrderByNextAttemptAtAsc(
            String status, OffsetDateTime now);
}
