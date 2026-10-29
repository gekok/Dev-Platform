package dev.platform.task.assignmentcleanup.persistence.entity;

import dev.platform.task.assignmentcleanup.MemberAssignmentCleanupStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "member_assignment_cleanup_jobs", indexes = {
        @Index(name = "idx_member_assignment_cleanup_due", columnList = "status, updated_at")
})
public class MemberAssignmentCleanupJobEntity {

    @Id
    @Column(name = "job_id", nullable = false)
    private UUID jobId;

    @Column(name = "deduplication_key", nullable = false, unique = true, length = 220)
    private String deduplicationKey;

    @Column(name = "event_id", nullable = false, unique = true)
    private UUID eventId;

    @Column(name = "operation_id", nullable = false)
    private UUID operationId;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "user_id", nullable = false, columnDefinition = "uuid")
    private UUID userId;

    @Column(name = "membership_version", nullable = false)
    private long membershipVersion;

    @Column(name = "source_aggregate_version", nullable = false)
    private long sourceAggregateVersion;

    @Column(name = "board_ids_json", nullable = false, columnDefinition = "TEXT")
    private String boardIdsJson;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MemberAssignmentCleanupStatus status = MemberAssignmentCleanupStatus.PENDING;

    @Column(name = "last_processed_card_id")
    private Long lastProcessedCardId;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @Column(name = "created_at", nullable = false, columnDefinition = "timestamptz")
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false, columnDefinition = "timestamptz")
    private OffsetDateTime updatedAt;

    @Column(name = "next_attempt_at", nullable = false, columnDefinition = "timestamptz")
    private OffsetDateTime nextAttemptAt;

    public MemberAssignmentCleanupJobEntity(
            UUID jobId,
            String deduplicationKey,
            UUID eventId,
            UUID operationId,
            Long projectId,
            UUID userId,
            long membershipVersion,
            long sourceAggregateVersion,
            String boardIdsJson,
            OffsetDateTime now) {
        this.jobId = jobId;
        this.deduplicationKey = deduplicationKey;
        this.eventId = eventId;
        this.operationId = operationId;
        this.projectId = projectId;
        this.userId = userId;
        this.membershipVersion = membershipVersion;
        this.sourceAggregateVersion = sourceAggregateVersion;
        this.boardIdsJson = boardIdsJson;
        this.status = MemberAssignmentCleanupStatus.PENDING;
        this.lastProcessedCardId = 0L;
        this.createdAt = now;
        this.updatedAt = now;
        this.nextAttemptAt = now;
    }

    public void markBatchProcessed(Long lastCardId, boolean hasMore, OffsetDateTime now) {
        lastProcessedCardId = lastCardId;
        updatedAt = now;
        status = hasMore ? MemberAssignmentCleanupStatus.PENDING : MemberAssignmentCleanupStatus.COMPLETED;
        if (!hasMore) {
            lastError = null;
        }
    }

    public void scheduleRetry(OffsetDateTime now, String error) {
        attemptCount++;
        nextAttemptAt = now.plusSeconds(Math.min(300L, 1L << Math.min(attemptCount, 8)));
        updatedAt = now;
        lastError = error;
        status = MemberAssignmentCleanupStatus.PENDING;
    }
}
