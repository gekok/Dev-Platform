package dev.platform.board.infrastructure.persistence.repository;

import dev.platform.board.infrastructure.persistence.entity.ProjectEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ProjectJpaRepository extends JpaRepository<ProjectEntity, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select project from ProjectEntity project where project.id = :projectId")
    Optional<ProjectEntity> findForUpdate(@Param("projectId") Long projectId);

    @Query("""
            select project
            from ProjectEntity project
            join ProjectMembershipEntity membership
              on membership.id.projectId = project.id
            where membership.id.userId = :userId and membership.status = 'ACTIVE'
            order by project.updatedAt desc, project.id desc
            """)
    Page<ProjectEntity> findActiveProjectsForUser(@Param("userId") UUID userId, Pageable pageable);
}
