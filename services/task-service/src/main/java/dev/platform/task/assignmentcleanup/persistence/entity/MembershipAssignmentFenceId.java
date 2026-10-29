package dev.platform.task.assignmentcleanup.persistence.entity;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Embeddable
public class MembershipAssignmentFenceId implements Serializable {

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "user_id", nullable = false, columnDefinition = "uuid")
    private UUID userId;

    @Column(name = "membership_version", nullable = false)
    private long membershipVersion;

    public MembershipAssignmentFenceId(Long projectId, UUID userId, long membershipVersion) {
        this.projectId = projectId;
        this.userId = userId;
        this.membershipVersion = membershipVersion;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof MembershipAssignmentFenceId that)) {
            return false;
        }
        return membershipVersion == that.membershipVersion
                && Objects.equals(projectId, that.projectId)
                && Objects.equals(userId, that.userId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(projectId, userId, membershipVersion);
    }
}
