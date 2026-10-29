package dev.platform.task.assignmentcleanup.persistence.repository;

import dev.platform.task.assignmentcleanup.persistence.entity.MembershipAssignmentFenceEntity;
import dev.platform.task.assignmentcleanup.persistence.entity.MembershipAssignmentFenceId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MembershipAssignmentFenceJpaRepository
        extends JpaRepository<MembershipAssignmentFenceEntity, MembershipAssignmentFenceId> {
}
