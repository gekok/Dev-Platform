package dev.platform.board.infrastructure.persistence.entity;

import java.time.OffsetDateTime;
import java.util.UUID;
import jakarta.persistence.*;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "invitations", indexes = {
        @Index(name = "ix_invitations_user_status_created", columnList = "user_id,status,created_at"),
        @Index(name = "ix_invitations_project_status_created", columnList = "project_id,status,created_at")
})
public class InvitationEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", insertable = false, updatable = false)
    private ProjectEntity project;

    @Column(name = "user_id", nullable = false, columnDefinition = "uniqueidentifier")
    private UUID userId;

    @Column(name = "invited_by_user_id", nullable = false, columnDefinition = "uniqueidentifier")
    private UUID invitedByUserId;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "created_at", nullable = false, columnDefinition = "datetimeoffset")
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false, columnDefinition = "datetimeoffset")
    private OffsetDateTime updatedAt;

    @Column(name = "responded_at", columnDefinition = "datetimeoffset")
    private OffsetDateTime respondedAt;
}
