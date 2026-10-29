package dev.platform.user.infrastructure.persistence.repository;

import dev.platform.user.infrastructure.persistence.entity.UserSystemRoleEntity;
import dev.platform.user.infrastructure.persistence.entity.UserSystemRoleId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface UserSystemRoleJpaRepository extends JpaRepository<UserSystemRoleEntity, UserSystemRoleId> {

    List<UserSystemRoleEntity> findByIdUserId(UUID userId);
}
