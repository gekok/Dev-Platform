package dev.platform.user.infrastructure.persistence.repository;

import dev.platform.user.infrastructure.persistence.entity.UserEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

// Kho JPA cho UserEntity.
public interface UserJpaRepository extends JpaRepository<UserEntity, UUID> {
    Optional<UserEntity> findByEmail(String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select account from UserEntity account where account.id = :accountId")
    Optional<UserEntity> findByIdForUpdate(@Param("accountId") UUID accountId);
}
