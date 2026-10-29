package dev.platform.task.infrastructure.persistence.repository;

import dev.platform.task.infrastructure.persistence.entity.TaskAssigneeEntity;
import dev.platform.task.infrastructure.persistence.entity.TaskAssigneeId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TaskAssigneeJpaRepository extends JpaRepository<TaskAssigneeEntity, TaskAssigneeId> {

    List<TaskAssigneeEntity> findByIdTaskId(Long taskId);

    List<TaskAssigneeEntity> findByIdTaskIdIn(Collection<Long> taskIds);

    Optional<TaskAssigneeEntity> findFirstByIdTaskIdAndPrimaryTrue(Long taskId);

    void deleteByIdTaskId(Long taskId);

    List<TaskAssigneeEntity> findTop100ByIdUserIdAndMembershipVersionAndCardProjectIdAndCardBoardIdInAndCardIdGreaterThanOrderByCardIdAsc(
            UUID userId, long membershipVersion, Long projectId, Collection<Long> boardIds, Long afterCardId);
}
