package dev.platform.user.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Nationalized;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "system_roles")
public class SystemRoleEntity {

  @Id
  @Column(name = "role_code", nullable = false, length = 40)
  private String roleCode;

  @Nationalized
  @Column(name = "description", length = 200)
  private String description;
}
