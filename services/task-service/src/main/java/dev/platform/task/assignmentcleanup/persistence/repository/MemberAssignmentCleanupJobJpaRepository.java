package dev.platform.task.assignmentcleanup.persistence.repository;

import dev.platform.task.assignmentcleanup.persistence.entity.MemberAssignmentCleanupJobEntity;
import dev.platform.task.assignmentcleanup.MemberAssignmentCleanupStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;
import java.time.OffsetDateTime;
import java.util.UUID;

public interface MemberAssignmentCleanupJobJpaRepository
        extends JpaRepository<MemberAssignmentCleanupJobEntity, UUID> {

    Optional<MemberAssignmentCleanupJobEntity> findByDeduplicationKey(String deduplicationKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<MemberAssignmentCleanupJobEntity> findFirstByStatusAndNextAttemptAtLessThanEqualOrderByNextAttemptAtAsc(
            MemberAssignmentCleanupStatus status, OffsetDateTime now);
}
