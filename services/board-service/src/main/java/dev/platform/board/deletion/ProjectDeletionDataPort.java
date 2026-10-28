package dev.platform.board.deletion;

import java.util.List;
import java.util.UUID;

/** Implement after Project, Membership, and Invitation are added to board-service. */
public interface ProjectDeletionDataPort {

    List<ProjectDeletionTarget> findOwnedProjects(UUID ownerId);

    List<Long> findMemberProjectIds(UUID userId);

    List<Long> findBoardIds(Long projectId);

    void cancelPendingInvitations(UUID userId);

    void blockProjectWrites(Long projectId, UUID projectDeletionId);

    void removeMembership(UUID userId, Long projectId);

    void deleteProjectGraph(Long projectId);
}
