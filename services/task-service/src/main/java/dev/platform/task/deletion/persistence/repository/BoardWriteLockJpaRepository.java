package dev.platform.task.deletion.persistence.repository;

import dev.platform.task.deletion.persistence.entity.BoardWriteLockEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BoardWriteLockJpaRepository extends JpaRepository<BoardWriteLockEntity, Long> {
}
