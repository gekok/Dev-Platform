package dev.platform.board.application.port.out;

import dev.platform.board.application.query.ProjectPage;
import dev.platform.board.application.query.InvitationInboxItem;
import dev.platform.board.domain.aggregate.Invitation;
import dev.platform.board.domain.aggregate.MembershipVersion;
import dev.platform.board.domain.aggregate.Project;
import dev.platform.board.domain.aggregate.ProjectMembership;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProjectDataPort {
    Project saveProject(Project project);

    Optional<Project> findProject(Long projectId);

    Optional<Project> findProjectForUpdate(Long projectId);

    ProjectPage<Project> listProjects(UUID userId, int page, int size);

    ProjectMembership saveMembership(ProjectMembership membership);

    Optional<ProjectMembership> findMembership(Long projectId, UUID userId);

    Optional<ProjectMembership> findMembershipForUpdate(Long projectId, UUID userId);

    ProjectPage<ProjectMembership> listMembers(Long projectId, int page, int size);

    MembershipVersion saveVersion(MembershipVersion version);

    Optional<MembershipVersion> findVersionForUpdate(Long projectId, UUID userId);

    Optional<MembershipVersion> findVersion(Long projectId, UUID userId);

    void flushMemberships();

    Invitation saveInvitation(Invitation invitation);

    Optional<Invitation> findInvitation(Long invitationId);

    Optional<Invitation> findInvitationForUpdate(Long invitationId);

    Optional<Invitation> findInvitation(Long invitationId, Long projectId);

    boolean hasPendingInvitation(Long projectId, UUID userId);

    ProjectPage<Invitation> listProjectInvitations(Long projectId, String status, int page, int size);

    ProjectPage<InvitationInboxItem> listUserInvitations(UUID userId, String status, int page, int size);

    List<Long> listBoardIds(Long projectId);

}
