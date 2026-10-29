package dev.platform.user.infrastructure.persistence.repository;

import dev.platform.user.infrastructure.persistence.entity.SystemRoleEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SystemRoleJpaRepository extends JpaRepository<SystemRoleEntity, String> {
}
