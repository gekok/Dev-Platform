package dev.platform.task.infrastructure.persistence.entity;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "task_assignees")
public class TaskAssigneeEntity {

    @EmbeddedId
    private TaskAssigneeId id;

    @MapsId("taskId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "task_id", nullable = false)
    private CardEntity card;

    @Column(name = "assigned_by_user_id", nullable = false, columnDefinition = "uuid")
    private UUID assignedByUserId;

    @Column(name = "assigned_at", nullable = false, columnDefinition = "timestamptz")
    private OffsetDateTime assignedAt;

    @Column(name = "membership_version", nullable = false)
    private long membershipVersion;

    @Column(name = "is_primary", nullable = false)
    private boolean primary;

    public TaskAssigneeEntity(
            CardEntity card,
            UUID userId,
            UUID assignedByUserId,
            OffsetDateTime assignedAt,
            long membershipVersion) {
        this.id = new TaskAssigneeId(card.getId(), userId);
        this.card = card;
        this.assignedByUserId = assignedByUserId;
        this.assignedAt = assignedAt;
        this.membershipVersion = membershipVersion;
        this.primary = true;
    }
}
