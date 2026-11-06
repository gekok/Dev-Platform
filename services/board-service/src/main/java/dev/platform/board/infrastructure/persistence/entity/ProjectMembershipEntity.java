package dev.platform.board.infrastructure.persistence.entity;

import java.time.OffsetDateTime;
import jakarta.persistence.*;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "project_memberships", indexes = @Index(name = "ix_project_memberships_user_status_project", columnList = "user_id,status,project_id"))
public class ProjectMembershipEntity {
    @EmbeddedId
    private ProjectMembershipId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", insertable = false, updatable = false)
    private ProjectEntity project;

    @Column(nullable = false, length = 20)
    private String role;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "joined_at", nullable = false, columnDefinition = "datetimeoffset")
    private OffsetDateTime joinedAt;

    @Column(name = "removed_at", columnDefinition = "datetimeoffset")
    private OffsetDateTime removedAt;

    @Column(name = "updated_at", nullable = false, columnDefinition = "datetimeoffset")
    private OffsetDateTime updatedAt;
}
