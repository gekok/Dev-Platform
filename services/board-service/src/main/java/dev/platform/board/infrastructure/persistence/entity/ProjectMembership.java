package dev.platform.board.infrastructure.persistence.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
@Entity
@Table(name = "project_memberships")
public class ProjectMembership {
    @EmbeddedId
    private ProjectMembershipId id;

    @MapsId("projectId")
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Size(max = 20)
    @NotNull
    @Column(name = "role", nullable = false, length = 20)
    private String role;

    @Size(max = 20)
    @NotNull
    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @NotNull
    @Column(name = "joined_at", nullable = false)
    private OffsetDateTime joinedAt;

    @Column(name = "removed_at")
    private OffsetDateTime removedAt;

    @NotNull
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

}