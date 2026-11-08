package dev.platform.board.infrastructure.persistence.repository;

import dev.platform.board.infrastructure.persistence.entity.ProjectMembershipEntity;
import dev.platform.board.infrastructure.persistence.entity.ProjectMembershipId;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProjectMembershipJpaRepository
        extends JpaRepository<ProjectMembershipEntity, ProjectMembershipId> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select membership from ProjectMembershipEntity membership where membership.id = :id")
    Optional<ProjectMembershipEntity> findForUpdate(@Param("id") ProjectMembershipId id);

    Page<ProjectMembershipEntity> findByIdProjectIdAndStatus(
            Long projectId, String status, Pageable pageable);

    List<ProjectMembershipEntity> findByIdProjectIdAndStatusOrderByRoleAscJoinedAtAsc(
            Long projectId, String status);

    boolean existsByIdProjectIdAndIdUserIdAndStatus(Long projectId, UUID userId, String status);
}
