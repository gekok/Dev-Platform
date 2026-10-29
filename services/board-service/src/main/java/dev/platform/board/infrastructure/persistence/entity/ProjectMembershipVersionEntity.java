package dev.platform.board.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "project_membership_versions")
public class ProjectMembershipVersionEntity {

    @EmbeddedId
    private ProjectMembershipVersionId id;

    @Column(name = "membership_version", nullable = false)
    private long membershipVersion;

    @Column(name = "authorization_version", nullable = false)
    private long authorizationVersion;

    @Version
    @Column(name = "row_version", nullable = false)
    private long rowVersion;

    public ProjectMembershipVersionEntity(
            ProjectMembershipVersionId id, long membershipVersion, long authorizationVersion) {
        this.id = id;
        this.membershipVersion = membershipVersion;
        this.authorizationVersion = authorizationVersion;
    }

    public long nextMembershipVersion() {
        membershipVersion++;
        authorizationVersion++;
        return membershipVersion;
    }

    public long nextAuthorizationVersion() {
        return ++authorizationVersion;
    }
}
