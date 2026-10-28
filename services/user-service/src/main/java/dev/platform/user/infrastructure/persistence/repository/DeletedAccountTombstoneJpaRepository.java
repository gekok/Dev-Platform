package dev.platform.user.infrastructure.persistence.repository;

import dev.platform.user.infrastructure.persistence.entity.DeletedAccountTombstoneEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface DeletedAccountTombstoneJpaRepository extends JpaRepository<DeletedAccountTombstoneEntity, UUID> {
}
