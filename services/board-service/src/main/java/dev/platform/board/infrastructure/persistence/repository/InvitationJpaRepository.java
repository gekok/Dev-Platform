package dev.platform.board.infrastructure.persistence.repository;

import dev.platform.board.infrastructure.persistence.entity.InvitationEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface InvitationJpaRepository extends JpaRepository<InvitationEntity, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select invitation from InvitationEntity invitation where invitation.id = :invitationId")
    Optional<InvitationEntity> findForUpdate(@Param("invitationId") Long invitationId);

    Page<InvitationEntity> findByProjectIdAndStatus(
            Long projectId, String status, Pageable pageable);

    Page<InvitationEntity> findByProjectId(Long projectId, Pageable pageable);

    Page<InvitationEntity> findByUserIdAndStatus(UUID userId, String status, Pageable pageable);

    Page<InvitationEntity> findByUserId(UUID userId, Pageable pageable);

    boolean existsByProjectIdAndUserIdAndStatus(Long projectId, UUID userId, String status);

    Optional<InvitationEntity> findByIdAndProjectId(Long invitationId, Long projectId);
}
