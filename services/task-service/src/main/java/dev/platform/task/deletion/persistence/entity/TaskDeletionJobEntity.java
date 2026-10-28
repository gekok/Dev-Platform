package dev.platform.task.deletion.persistence.entity;

import dev.platform.task.deletion.TaskDeletionJobType;
import dev.platform.task.deletion.TaskDeletionStatus;
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
@Table(name = "task_deletion_jobs", indexes = {
        @Index(name = "idx_task_deletion_status", columnList = "status, created_at")
})
public class TaskDeletionJobEntity {

    @Id
    @Column(name = "job_id", nullable = false)
    private UUID jobId;

    @Column(name = "deduplication_key", nullable = false, unique = true, length = 200)
    private String deduplicationKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "job_type", nullable = false, length = 40)
    private TaskDeletionJobType jobType;

    @Column(name = "operation_id", nullable = false)
    private UUID operationId;

    @Column(name = "project_deletion_id")
    private UUID projectDeletionId;

    @Column(name = "project_id")
    private Long projectId;

    @Column(name = "board_ids_json", nullable = false, columnDefinition = "TEXT")
    private String boardIdsJson;

    @Column(name = "target_user_id", columnDefinition = "uuid")
    private UUID targetUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TaskDeletionStatus status = TaskDeletionStatus.PENDING;

    @Column(name = "last_processed_comment_id")
    private Long lastProcessedCommentId;

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

    public TaskDeletionJobEntity(
            UUID jobId,
            String deduplicationKey,
            TaskDeletionJobType jobType,
            UUID operationId,
            UUID projectDeletionId,
            Long projectId,
            String boardIdsJson,
            UUID targetUserId,
            OffsetDateTime now) {
        this.jobId = jobId;
        this.deduplicationKey = deduplicationKey;
        this.jobType = jobType;
        this.operationId = operationId;
        this.projectDeletionId = projectDeletionId;
        this.projectId = projectId;
        this.boardIdsJson = boardIdsJson;
        this.targetUserId = targetUserId;
        this.status = TaskDeletionStatus.PENDING;
        this.lastProcessedCommentId = 0L;
        this.lastProcessedCardId = 0L;
        this.createdAt = now;
        this.updatedAt = now;
    }
}
