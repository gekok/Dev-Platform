package dev.platform.board.application.port.in;

import dev.platform.board.application.command.*;
import dev.platform.board.application.query.*;
import dev.platform.board.domain.aggregate.Invitation;
import dev.platform.board.domain.aggregate.Project;
import dev.platform.board.domain.aggregate.ProjectMembership;

import java.util.Optional;

public interface ProjectUseCasePort {
    ProjectView create(CreateProjectCommand command);

    ProjectPage<ProjectView> listMine(ProjectListQuery query);

    ProjectView get(ProjectQuery query);

    ProjectView rename(ProjectNameCommand command);

    ProjectView changeStatus(ChangeProjectStatusCommand command);

    Invitation invite(InviteMemberCommand command);

    ProjectPage<Invitation> listProjectInvitations(InvitationsQuery query);

    Invitation cancelInvitation(CancelInvitationCommand command);

    ProjectPage<InvitationInboxItem> listMyInvitations(InvitationInboxQuery query);

    ProjectMembership acceptInvitation(InvitationResponseCommand command);

    Invitation rejectInvitation(InvitationResponseCommand command);

    ProjectPage<ProjectMembership> listMembers(MembersQuery query);

    void removeMember(RemoveMemberCommand command);

    void leaveProject(Long projectId, java.util.UUID actorId);

    ProjectMembership transferOwnership(TransferOwnershipCommand command);

    MemberAccess verifyMember(MemberAccessQuery query);
}
