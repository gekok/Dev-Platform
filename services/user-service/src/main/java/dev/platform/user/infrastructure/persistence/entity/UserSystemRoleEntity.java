package dev.platform.user.infrastructure.persistence.entity;

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
@Table(name = "user_system_roles")
public class UserSystemRoleEntity {

    @EmbeddedId
    private UserSystemRoleId id;

    @MapsId("userId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    @MapsId("roleCode")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "role_code", nullable = false)
    private SystemRoleEntity role;

    @Column(name = "granted_at", nullable = false, columnDefinition = "timestamptz")
    private OffsetDateTime grantedAt;

    @Column(name = "granted_by", columnDefinition = "uuid")
    private UUID grantedBy;
}
