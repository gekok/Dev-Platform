package dev.platform.task.deletion.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@NoArgsConstructor
@Entity
@Table(name = "board_write_locks")
public class BoardWriteLockEntity {

    @Id
    @Column(name = "board_id", nullable = false)
    private Long boardId;

    @Column(name = "project_deletion_id", nullable = false)
    private UUID projectDeletionId;

    @Column(name = "created_at", nullable = false, columnDefinition = "timestamptz")
    private OffsetDateTime createdAt;

    public BoardWriteLockEntity(Long boardId, UUID projectDeletionId, OffsetDateTime createdAt) {
        this.boardId = boardId;
        this.projectDeletionId = projectDeletionId;
        this.createdAt = createdAt;
    }
}
