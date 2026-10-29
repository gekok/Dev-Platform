package dev.platform.user.infrastructure.persistence.entity;

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
public class UserSystemRoleId implements Serializable {

    @Column(name = "user_id", nullable = false, columnDefinition = "uuid")
    private UUID userId;

    @Column(name = "role_code", nullable = false, length = 40)
    private String roleCode;

    public UserSystemRoleId(UUID userId, String roleCode) {
        this.userId = userId;
        this.roleCode = roleCode;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof UserSystemRoleId that)) {
            return false;
        }
        return Objects.equals(userId, that.userId) && Objects.equals(roleCode, that.roleCode);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, roleCode);
    }
}
