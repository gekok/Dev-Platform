package dev.platform.task.assignmentcleanup.persistence.repository;

import dev.platform.task.assignmentcleanup.persistence.entity.MemberAssignmentCleanupJobEntity;
import dev.platform.task.assignmentcleanup.MemberAssignmentCleanupStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.time.OffsetDateTime;
import java.util.UUID;

public interface MemberAssignmentCleanupJobJpaRepository
        extends JpaRepository<MemberAssignmentCleanupJobEntity, UUID> {

    Optional<MemberAssignmentCleanupJobEntity> findByDeduplicationKey(String deduplicationKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select j from MemberAssignmentCleanupJobEntity j where j.jobId = :jobId")
    Optional<MemberAssignmentCleanupJobEntity> lockByJobId(@Param("jobId") UUID jobId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<MemberAssignmentCleanupJobEntity> findFirstByStatusAndNextAttemptAtLessThanEqualOrderByNextAttemptAtAsc(
            MemberAssignmentCleanupStatus status, OffsetDateTime now);
}
