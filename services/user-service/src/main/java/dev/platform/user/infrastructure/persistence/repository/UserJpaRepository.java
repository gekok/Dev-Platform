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

  @Query("""
      SELECT u FROM UserEntity u
      WHERE u.status = :status
        AND (:query IS NULL OR :query = '' OR LOWER(u.email) LIKE LOWER(CONCAT('%', :query, '%')) OR LOWER(u.fullName) LIKE LOWER(CONCAT('%', :query, '%')))
      ORDER BY u.fullName ASC
      """)
  List<UserEntity> searchActiveUsers(
      @Param("status") AccountStatus status,
      @Param("query") String query,
      Pageable pageable);

  List<UserEntity> findByIdIn(Collection<UUID> ids);
}
