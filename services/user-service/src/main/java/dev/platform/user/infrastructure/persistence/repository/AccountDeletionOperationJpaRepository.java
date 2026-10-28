package dev.platform.user.infrastructure.persistence.repository;

import dev.platform.user.infrastructure.persistence.entity.AccountDeletionOperationEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AccountDeletionOperationJpaRepository extends JpaRepository<AccountDeletionOperationEntity, UUID> {
}
