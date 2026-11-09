package dev.platform.user.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
@Embeddable
public class UserSystemRoleId implements Serializable {
  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(name = "role_code", nullable = false, length = 40)
  private String roleCode;
}
