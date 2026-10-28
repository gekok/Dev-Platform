package dev.platform.board.deletion.persistence.entity;

import dev.platform.board.deletion.ProjectDeletionStatus;
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
@Table(name = "project_deletion_operations", indexes = {
        @Index(name = "idx_project_deletion_parent", columnList = "operation_id"),
        @Index(name = "idx_project_deletion_project", columnList = "project_id")
})
public class ProjectDeletionOperationEntity {

    @Id
    @Column(name = "project_deletion_id", nullable = false)
    private UUID projectDeletionId;

    @Column(name = "operation_id", nullable = false)
    private UUID operationId;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "board_ids_json", nullable = false, columnDefinition = "TEXT")
    private String boardIdsJson;

    @Column(name = "task_data_deleted", nullable = false)
    private boolean taskDataDeleted;

    @Column(name = "notifications_deleted", nullable = false)
    private boolean notificationsDeleted;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ProjectDeletionStatus status;

    @Column(name = "created_at", nullable = false, columnDefinition = "timestamptz")
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false, columnDefinition = "timestamptz")
    private OffsetDateTime updatedAt;

    public ProjectDeletionOperationEntity(
            UUID projectDeletionId,
            UUID operationId,
            Long projectId,
            String boardIdsJson,
            OffsetDateTime now) {
        this.projectDeletionId = projectDeletionId;
        this.operationId = operationId;
        this.projectId = projectId;
        this.boardIdsJson = boardIdsJson;
        this.status = ProjectDeletionStatus.PENDING;
        this.createdAt = now;
        this.updatedAt = now;
    }
}
