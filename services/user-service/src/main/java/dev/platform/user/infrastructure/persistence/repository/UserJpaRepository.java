package dev.platform.user.infrastructure.persistence.repository;

import dev.platform.user.domain.AccountStatus;
import dev.platform.user.infrastructure.persistence.entity.UserEntity;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserJpaRepository extends JpaRepository<UserEntity, UUID> {
  Optional<UserEntity> findByEmail(String email);

  boolean existsByEmail(String email);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT u FROM UserEntity u WHERE u.id = :id")
  Optional<UserEntity> findByIdForUpdate(@Param("id") UUID id);

  @Query(
      """
      SELECT u FROM UserEntity u
      WHERE u.status = :status
        AND NOT EXISTS (
          SELECT r.id.userId FROM UserSystemRoleEntity r
          WHERE r.id.userId = u.id AND r.id.roleCode = 'SYSTEM_ADMIN'
        )
      ORDER BY u.fullName ASC
      """)
  List<UserEntity> findDirectoryCandidatesByStatus(
      @Param("status") AccountStatus status, Pageable pageable);

  @Query(
      """
      SELECT u FROM UserEntity u
      WHERE u.status = :status
        AND NOT EXISTS (
          SELECT r.id.userId FROM UserSystemRoleEntity r
          WHERE r.id.userId = u.id AND r.id.roleCode = 'SYSTEM_ADMIN'
        )
        AND (LOWER(u.email) LIKE LOWER(:pattern) OR LOWER(u.fullName) LIKE LOWER(:pattern))
      ORDER BY u.fullName ASC
      """)
  List<UserEntity> searchActiveUsersByPattern(
      @Param("status") AccountStatus status, @Param("pattern") String pattern, Pageable pageable);

  List<UserEntity> findByIdIn(Collection<UUID> ids);
}
