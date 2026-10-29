package dev.platform.board.infrastructure.persistence.repository;

import dev.platform.board.infrastructure.persistence.entity.ProjectMembershipVersionEntity;
import dev.platform.board.infrastructure.persistence.entity.ProjectMembershipVersionId;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ProjectMembershipVersionJpaRepository
        extends JpaRepository<ProjectMembershipVersionEntity, ProjectMembershipVersionId> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select version from ProjectMembershipVersionEntity version where version.id = :id")
    Optional<ProjectMembershipVersionEntity> findForUpdate(@Param("id") ProjectMembershipVersionId id);
}
