package dev.platform.user.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "system_roles")
public class SystemRoleEntity {

    @Id
    @Column(name = "role_code", nullable = false, length = 40)
    private String roleCode;

    @Column(length = 200)
    private String description;

    public SystemRoleEntity(String roleCode, String description) {
        this.roleCode = roleCode;
        this.description = description;
    }
}
