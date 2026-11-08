package dev.platform.board.application.service;

import dev.platform.board.application.command.*;
import dev.platform.board.application.port.in.ProjectUseCasePort;
import dev.platform.board.application.port.out.*;
import dev.platform.board.application.query.*;
import dev.platform.board.domain.aggregate.*;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProjectApplicationService implements ProjectUseCasePort {
    private static final int MAX_PAGE_SIZE = 100;

    private final ProjectDataPort data;
    private final MembershipSnapshotPort snapshots;
    private final ProjectEventPort events;
    private final AccountLookupPort accounts;

    @Override
    @Transactional
    public ProjectView create(CreateProjectCommand command) {
        requireActor(command.actorId());
        OffsetDateTime now = now();
        Project created = data.saveProject(new Project(null, Project.validateName(command.name()),
                Project.Status.ACTIVE, command.actorId(), now, now));
        ProjectMembership owner = new ProjectMembership(created.id(), command.actorId(),
                ProjectMembership.Role.OWNER, ProjectMembership.Status.ACTIVE, now, null, now);
        data.saveMembership(owner);
        MembershipVersion version = data.saveVersion(MembershipVersion.initial(created.id(), command.actorId()));
        snapshots.record(UUID.randomUUID(), created.id(), command.actorId(), "ACTIVE", "OWNER",
                version.membershipVersion(), version.authorizationVersion());
        return new ProjectView(created, "OWNER");
    }

    @Override
    @Transactional(readOnly = true)
    public ProjectPage<ProjectView> listMine(ProjectListQuery query) {
        requireActor(query.actorId());
        validatePage(query.page(), query.size());
        ProjectPage<Project> projects = data.listProjects(query.actorId(), query.page(), query.size());
        List<ProjectView> items = projects.items().stream()
                .map(project -> new ProjectView(project,
                        data.findMembership(project.id(), query.actorId())
                                .filter(ProjectMembership::isActive)
                                .map(membership -> membership.role().name())
                                .orElseThrow(() -> ProjectRuleViolation.notFound("Project not found."))))
                .toList();
        return new ProjectPage<>(items, projects.page(), projects.size(), projects.totalElements());
    }

    @Override
    @Transactional(readOnly = true)
    public ProjectView get(ProjectQuery query) {
        Project project = requireProject(query.projectId());
        ProjectMembership membership = requireActiveMembership(project.id(), query.actorId());
        return new ProjectView(project, membership.role().name());
    }

    @Override
    @Transactional
    public ProjectView rename(ProjectNameCommand command) {
        Project project = requireProjectForUpdate(command.projectId());
        ProjectMembership membership = requireOwner(project.id(), command.actorId());
        Project renamed = project.rename(command.name(), now());
        return new ProjectView(renamed == project ? project : data.saveProject(renamed), membership.role().name());
    }

    @Override
    @Transactional
    public ProjectView changeStatus(ChangeProjectStatusCommand command) {
        Project project = requireProjectForUpdate(command.projectId());
        ProjectMembership membership = requireOwner(project.id(), command.actorId());
        Project changed = project.changeStatus(command.status(), now());
        if (changed == project) {
            return new ProjectView(project, membership.role().name());
        }
        Project saved = data.saveProject(changed);
        events.projectStatusChanged(project.id(), project.status().name(), saved.status().name());
        return new ProjectView(saved, membership.role().name());
    }

    @Override
    @Transactional
    public Invitation invite(InviteMemberCommand command) {
        requireActor(command.inviteeId());
        Project project = requireProjectForUpdate(command.projectId());
        requireOwner(project.id(), command.actorId());
        project.requireActive();
        if (command.inviteeId().equals(command.actorId())
                || data.findMembership(project.id(), command.inviteeId())
                        .filter(ProjectMembership::isActive).isPresent()) {
            throw ProjectRuleViolation.conflict("MEMBERSHIP_ALREADY_EXISTS",
                    "The invited account is already a Project member.");
        }
        if (data.hasPendingInvitation(project.id(), command.inviteeId())) {
            throw ProjectRuleViolation.conflict("INVITATION_ALREADY_PENDING",
                    "A pending invitation already exists for this account.");
        }
        if (!accounts.isActive(command.inviteeId())) {
            throw ProjectRuleViolation.unprocessable("INVITEE_NOT_AVAILABLE",
                    "Invitee account does not exist or is not ACTIVE.");
        }
        OffsetDateTime now = now();
        return data.saveInvitation(new Invitation(null, project.id(), command.inviteeId(),
                command.actorId(), Invitation.Status.PENDING, now, now, null));
    }

    @Override
    @Transactional(readOnly = true)
    public ProjectPage<Invitation> listProjectInvitations(InvitationsQuery query) {
        Project project = requireProject(query.projectId());
        requireOwner(project.id(), query.actorId());
        validatePage(query.page(), query.size());
        String status = validateInvitationStatus(query.status());
        return data.listProjectInvitations(project.id(), status, query.page(), query.size());
    }

    @Override
    @Transactional
    public Invitation cancelInvitation(CancelInvitationCommand command) {
        Project project = requireProjectForUpdate(command.projectId());
        requireOwner(project.id(), command.actorId());
        project.requireActive();
        Invitation invitation = data.findInvitationForUpdate(command.invitationId())
                .filter(item -> item.projectId().equals(project.id()))
                .orElseThrow(() -> ProjectRuleViolation.notFound("Invitation not found."));
        return data.saveInvitation(invitation.respond(Invitation.Status.CANCELLED, now()));
    }

    @Override
    @Transactional(readOnly = true)
    public ProjectPage<InvitationInboxItem> listMyInvitations(InvitationInboxQuery query) {
        requireActor(query.actorId());
        validatePage(query.page(), query.size());
        return data.listUserInvitations(query.actorId(), validateInvitationStatus(query.status()),
                query.page(), query.size());
    }

    @Override
    @Transactional
    public ProjectMembership acceptInvitation(InvitationResponseCommand command) {
        Invitation invitation = requireInviteeInvitation(command.invitationId(), command.actorId());
        Project project = requireProjectForUpdate(invitation.projectId());
        invitation = requireInviteeInvitation(command.invitationId(), command.actorId());
        project.requireActive();
        if (invitation.status() != Invitation.Status.PENDING) {
            throw ProjectRuleViolation.conflict("INVITATION_NOT_PENDING",
                    "Invitation is no longer pending.");
        }
        OffsetDateTime now = now();
        ProjectMembership existing = data.findMembershipForUpdate(project.id(), command.actorId()).orElse(null);
        if (existing != null && existing.isActive()) {
            throw ProjectRuleViolation.conflict("MEMBERSHIP_ALREADY_EXISTS",
                    "The account is already a Project member.");
        }
        if (!accounts.isActive(command.actorId())) {
            throw ProjectRuleViolation.conflict("ACCOUNT_DISABLED",
                    "The account is no longer active.");
        }
        ProjectMembership membership = existing == null
                ? new ProjectMembership(project.id(), command.actorId(), ProjectMembership.Role.MEMBER,
                        ProjectMembership.Status.ACTIVE, now, null, now)
                : existing.reactivateAsMember(now);
        data.saveInvitation(invitation.respond(Invitation.Status.ACCEPTED, now));
        ProjectMembership savedMembership = data.saveMembership(membership);
        MembershipVersion version = nextVersion(project.id(), command.actorId(), true);
        snapshots.record(UUID.randomUUID(), project.id(), command.actorId(), "ACTIVE", "MEMBER",
                version.membershipVersion(), version.authorizationVersion());
        events.memberAdded(project.id(), command.actorId(),
                version.membershipVersion(), version.authorizationVersion());
        return savedMembership;
    }

    @Override
    @Transactional
    public Invitation rejectInvitation(InvitationResponseCommand command) {
        Invitation invitation = requireInviteeInvitation(command.invitationId(), command.actorId());
        Project project = requireProjectForUpdate(invitation.projectId());
        invitation = requireInviteeInvitation(command.invitationId(), command.actorId());
        project.requireActive();
        return data.saveInvitation(invitation.respond(Invitation.Status.REJECTED, now()));
    }

    @Override
    @Transactional(readOnly = true)
    public ProjectPage<ProjectMembership> listMembers(MembersQuery query) {
        Project project = requireProject(query.projectId());
        requireActiveMembership(project.id(), query.actorId());
        validatePage(query.page(), query.size());
        return data.listMembers(project.id(), query.page(), query.size());
    }

    @Override
    @Transactional
    public void removeMember(RemoveMemberCommand command) {
        Project project = requireProjectForUpdate(command.projectId());
        requireOwner(project.id(), command.actorId());
        project.requireActive();
        revokeMembership(project, command.memberId());
    }

    @Override
    @Transactional
    public void leaveProject(Long projectId, UUID actorId) {
        Project project = requireProjectForUpdate(projectId);
        ProjectMembership membership = requireActiveMembership(project.id(), actorId);
        project.requireActive();
        if (membership.role() == ProjectMembership.Role.OWNER) {
            throw ProjectRuleViolation.conflict("OWNER_CANNOT_LEAVE",
                    "Transfer Project ownership before leaving.");
        }
        revokeMembership(project, actorId);
    }

    @Override
    @Transactional
    public ProjectMembership transferOwnership(TransferOwnershipCommand command) {
        if (command.newOwnerId() == null || command.newOwnerId().equals(command.actorId())) {
            throw ProjectRuleViolation.validation("newOwnerUserId must identify another Project member");
        }
        Project project = requireProjectForUpdate(command.projectId());
        ProjectMembership currentOwner = requireOwner(project.id(), command.actorId());
        project.requireActive();
        ProjectMembership nextOwner = data.findMembershipForUpdate(project.id(), command.newOwnerId())
                .filter(ProjectMembership::isActive)
                .orElseThrow(() -> ProjectRuleViolation.conflict(
                        "TARGET_NOT_PROJECT_MEMBER", "New Owner must be an active Project member."));
        if (nextOwner.role() != ProjectMembership.Role.MEMBER) {
            throw ProjectRuleViolation.conflict(
                    "TARGET_NOT_PROJECT_MEMBER", "New Owner must be an active Project member.");
        }

        try {
            OffsetDateTime now = now();
            ProjectMembership former = currentOwner.withRole(ProjectMembership.Role.MEMBER, now);
            ProjectMembership promoted = nextOwner.withRole(ProjectMembership.Role.OWNER, now);
            data.saveMembership(former);
            data.flushMemberships();
            data.saveMembership(promoted);
            MembershipVersion formerVersion = nextVersion(project.id(), currentOwner.userId(), true);
            MembershipVersion promotedVersion = nextVersion(project.id(), nextOwner.userId(), true);
            snapshots.record(UUID.randomUUID(), project.id(), currentOwner.userId(), "ACTIVE", "MEMBER",
                    formerVersion.membershipVersion(), formerVersion.authorizationVersion());
            snapshots.record(UUID.randomUUID(), project.id(), nextOwner.userId(), "ACTIVE", "OWNER",
                    promotedVersion.membershipVersion(), promotedVersion.authorizationVersion());
            events.ownerTransferred(project.id(), currentOwner.userId(), nextOwner.userId(),
                    Math.max(formerVersion.authorizationVersion(), promotedVersion.authorizationVersion()));
            return promoted;
        } catch (OptimisticLockingFailureException exception) {
            throw ProjectRuleViolation.conflict("VERSION_CONFLICT",
                    "Project membership changed concurrently; reload and retry.");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public MemberAccess verifyMember(MemberAccessQuery query) {
        Project project = requireProject(query.projectId());
        ProjectMembership membership = data.findMembership(query.projectId(), query.userId())
                .orElseThrow(() -> ProjectRuleViolation.notFound("Membership not found."));
        long version = data.findVersion(query.projectId(), query.userId())
                .map(MembershipVersion::membershipVersion)
                .orElseThrow(() -> ProjectRuleViolation.internal(
                        "MEMBERSHIP_VERSION_MISSING", "Membership version data is missing."));
        return new MemberAccess(membership.isActive(), membership.role().name(), version,
                project.status().name());
    }

    private void revokeMembership(Project project, UUID userId) {
        ProjectMembership target = data.findMembershipForUpdate(project.id(), userId)
                .filter(ProjectMembership::isActive)
                .orElseThrow(() -> ProjectRuleViolation.notFound("Membership not found."));
        if (target.role() == ProjectMembership.Role.OWNER) {
            throw ProjectRuleViolation.conflict("OWNER_CANNOT_LEAVE",
                    "The Project Owner cannot leave or be removed.");
        }
        try {
            ProjectMembership removed = target.remove(now());
            data.saveMembership(removed);
            MembershipVersion version = nextVersion(project.id(), userId, true);
            UUID operationId = UUID.randomUUID();
            events.memberRemoved(operationId, project.id(), userId,
                    version.membershipVersion(), version.authorizationVersion(), data.listBoardIds(project.id()));
        } catch (OptimisticLockingFailureException exception) {
            throw ProjectRuleViolation.conflict("VERSION_CONFLICT",
                    "Project membership changed concurrently; reload and retry.");
        }
    }

    private MembershipVersion nextVersion(Long projectId, UUID userId, boolean nextMembershipGeneration) {
        MembershipVersion current = data.findVersionForUpdate(projectId, userId)
                .orElse(MembershipVersion.initial(projectId, userId));
        MembershipVersion next = nextMembershipGeneration
                ? current.nextMembershipGeneration()
                : current.nextAuthorizationVersion();
        return data.saveVersion(next);
    }

    private Invitation requireInviteeInvitation(Long invitationId, UUID actorId) {
        requireActor(actorId);
        Invitation invitation = data.findInvitation(invitationId)
                .orElseThrow(() -> ProjectRuleViolation.notFound("Invitation not found."));
        if (!invitation.userId().equals(actorId)) {
            throw ProjectRuleViolation.notFound("Invitation not found.");
        }
        if (invitation.status() != Invitation.Status.PENDING) {
            throw ProjectRuleViolation.conflict("INVITATION_NOT_PENDING",
                    "Invitation is no longer pending.");
        }
        return invitation;
    }

    private Project requireProject(Long projectId) {
        return data.findProject(projectId)
                .orElseThrow(() -> ProjectRuleViolation.notFound("Project not found."));
    }

    private Project requireProjectForUpdate(Long projectId) {
        return data.findProjectForUpdate(projectId)
                .orElseThrow(() -> ProjectRuleViolation.notFound("Project not found."));
    }

    private ProjectMembership requireActiveMembership(Long projectId, UUID userId) {
        requireActor(userId);
        return data.findMembership(projectId, userId)
                .filter(ProjectMembership::isActive)
                .orElseThrow(() -> ProjectRuleViolation.notFound("Project not found."));
    }

    private ProjectMembership requireOwner(Long projectId, UUID userId) {
        ProjectMembership membership = requireActiveMembership(projectId, userId);
        if (membership.role() != ProjectMembership.Role.OWNER) {
            throw ProjectRuleViolation.forbidden("Only the Project Owner can perform this operation.");
        }
        return membership;
    }

    private void requireActor(UUID actorId) {
        if (actorId == null) {
            throw ProjectRuleViolation.unauthorized("Authenticated user identity is required.");
        }
    }

    private void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw ProjectRuleViolation.validation("page must be non-negative and size must be between 1 and 100.");
        }
    }

    private String validateInvitationStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return Invitation.Status.valueOf(status).name();
        } catch (IllegalArgumentException exception) {
            throw ProjectRuleViolation.validation("status must be PENDING, ACCEPTED, REJECTED, or CANCELLED.");
        }
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }

}
