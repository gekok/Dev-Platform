package dev.platform.task.todo.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TodoTaskRepository extends JpaRepository<TodoTaskEntity, Long> {
    List<TodoTaskEntity> findByOwnerIdOrderByDeadlineAscCreatedAtDesc(UUID ownerId);

    Optional<TodoTaskEntity> findByIdAndOwnerId(Long id, UUID ownerId);
}
