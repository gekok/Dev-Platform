package dev.platform.notification.deletion.persistence.entity;

import dev.platform.notification.deletion.NotificationDeletionStatus;
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
@Table(name = "notification_deletion_jobs", indexes = {
        @Index(name = "idx_notification_deletion_status", columnList = "status, created_at")
})
public class NotificationDeletionJobEntity {

    @Id
    @Column(name = "project_deletion_id", nullable = false)
    private UUID projectDeletionId;

    @Column(name = "operation_id", nullable = false)
    private UUID operationId;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "board_ids_json", nullable = false, columnDefinition = "TEXT")
    private String boardIdsJson;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NotificationDeletionStatus status = NotificationDeletionStatus.PENDING;

    @Column(name = "last_processed_notification_id")
    private Long lastProcessedNotificationId;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @Column(name = "created_at", nullable = false, columnDefinition = "timestamptz")
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false, columnDefinition = "timestamptz")
    private OffsetDateTime updatedAt;

    public NotificationDeletionJobEntity(
            UUID projectDeletionId,
            UUID operationId,
            Long projectId,
            String boardIdsJson,
            OffsetDateTime now) {
        this.projectDeletionId = projectDeletionId;
        this.operationId = operationId;
        this.projectId = projectId;
        this.boardIdsJson = boardIdsJson;
        this.status = NotificationDeletionStatus.PENDING;
        this.lastProcessedNotificationId = 0L;
        this.createdAt = now;
        this.updatedAt = now;
    }
}
