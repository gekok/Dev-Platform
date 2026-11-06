package dev.platform.board.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.Hibernate;

import java.util.Objects;
import java.util.UUID;

@Getter
@Setter
@Embeddable
public class ProjectMembershipId implements java.io.Serializable {
    private static final long serialVersionUID = 3706768894382280110L;
    @NotNull
    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @NotNull
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || Hibernate.getClass(this) != Hibernate.getClass(o)) return false;
        ProjectMembershipId entity = (ProjectMembershipId) o;
        return Objects.equals(this.projectId, entity.projectId) &&
                Objects.equals(this.userId, entity.userId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(projectId, userId);
    }

}