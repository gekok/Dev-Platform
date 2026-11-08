package dev.platform.user.infrastructure.persistence.repository;

import dev.platform.user.infrastructure.persistence.entity.UserSystemRoleEntity;
import dev.platform.user.infrastructure.persistence.entity.UserSystemRoleId;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserSystemRoleJpaRepository
    extends JpaRepository<UserSystemRoleEntity, UserSystemRoleId> {

  List<UserSystemRoleEntity> findByIdUserId(UUID userId);

  List<UserSystemRoleEntity> findByIdUserIdIn(Collection<UUID> userIds);
}
