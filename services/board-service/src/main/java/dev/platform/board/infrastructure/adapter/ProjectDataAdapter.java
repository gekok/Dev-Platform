package dev.platform.board.infrastructure.adapter;

import dev.platform.board.application.port.out.ProjectDataPort;
import dev.platform.board.application.query.ProjectPage;
import dev.platform.board.application.query.InvitationInboxItem;
import dev.platform.board.application.query.SnapshotSyncJobView;
import dev.platform.board.domain.aggregate.Invitation;
import dev.platform.board.domain.aggregate.MembershipVersion;
import dev.platform.board.domain.aggregate.Project;
import dev.platform.board.domain.aggregate.ProjectMembership;
import dev.platform.board.infrastructure.persistence.entity.*;
import dev.platform.board.infrastructure.persistence.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ProjectDataAdapter implements ProjectDataPort {
    private final ProjectJpaRepository projects;
    private final ProjectMembershipJpaRepository memberships;
    private final ProjectMembershipVersionJpaRepository versions;
    private final InvitationJpaRepository invitations;
    private final BoardJpaRepository boards;
    private final ProjectAuthorizationSnapshotSyncJobJpaRepository syncJobRepository;

    @Override
    public Project saveProject(Project project) {
        ProjectEntity entity = project.id() == null
                ? new ProjectEntity()
                : projects.findById(project.id()).orElseGet(ProjectEntity::new);
        entity.setId(project.id());
        entity.setName(project.name());
        entity.setStatus(project.status().name());
        entity.setCreatedByUserId(project.createdByUserId());
        entity.setCreatedAt(project.createdAt());
        entity.setUpdatedAt(project.updatedAt());
        return toProject(projects.save(entity));
    }

    @Override
    public Optional<Project> findProject(Long projectId) {
        return projects.findById(projectId).map(this::toProject);
    }

    @Override
    public Optional<Project> findProjectForUpdate(Long projectId) {
        return projects.findForUpdate(projectId).map(this::toProject);
    }

    @Override
    public ProjectPage<Project> listProjects(UUID userId, int page, int size) {
        return projectPage(projects.findActiveProjectsForUser(userId, PageRequest.of(page, size)));
    }

    @Override
    public ProjectMembership saveMembership(ProjectMembership membership) {
        ProjectMembershipId id = new ProjectMembershipId(membership.projectId(), membership.userId());
        ProjectMembershipEntity entity = memberships.findById(id).orElseGet(ProjectMembershipEntity::new);
        entity.setId(id);
        entity.setRole(membership.role().name());
        entity.setStatus(membership.status().name());
        entity.setJoinedAt(membership.joinedAt());
        entity.setRemovedAt(membership.removedAt());
        entity.setUpdatedAt(membership.updatedAt());
        return toMembership(memberships.save(entity));
    }

    @Override
    public Optional<ProjectMembership> findMembership(Long projectId, UUID userId) {
        return memberships.findById(new ProjectMembershipId(projectId, userId)).map(this::toMembership);
    }

    @Override
    public Optional<ProjectMembership> findMembershipForUpdate(Long projectId, UUID userId) {
        return memberships.findForUpdate(new ProjectMembershipId(projectId, userId)).map(this::toMembership);
    }

    @Override
    public ProjectPage<ProjectMembership> listMembers(Long projectId, int page, int size) {
        return membershipPage(memberships.findByIdProjectIdAndStatus(
                projectId, ProjectMembership.Status.ACTIVE.name(), PageRequest.of(page, size)));
    }

    @Override
    public MembershipVersion saveVersion(MembershipVersion version) {
        ProjectMembershipVersionId id = new ProjectMembershipVersionId(version.projectId(), version.userId());
        ProjectMembershipVersionEntity entity = versions.findForUpdate(id).orElseGet(() -> {
            ProjectMembershipVersionEntity created = new ProjectMembershipVersionEntity();
            created.setId(id);
            return created;
        });
        entity.setMembershipVersion(version.membershipVersion());
        entity.setAuthorizationVersion(version.authorizationVersion());
        ProjectMembershipVersionEntity saved = versions.save(entity);
        return toVersion(saved);
    }

    @Override
    public Optional<MembershipVersion> findVersionForUpdate(Long projectId, UUID userId) {
        return versions.findForUpdate(new ProjectMembershipVersionId(projectId, userId)).map(this::toVersion);
    }

    @Override
    public Optional<MembershipVersion> findVersion(Long projectId, UUID userId) {
        return versions.findById(new ProjectMembershipVersionId(projectId, userId)).map(this::toVersion);
    }

    @Override
    public void flushMemberships() {
        memberships.flush();
    }

    @Override
    public Invitation saveInvitation(Invitation invitation) {
        InvitationEntity entity = invitation.id() == null
                ? new InvitationEntity()
                : invitations.findById(invitation.id()).orElseGet(InvitationEntity::new);
        entity.setId(invitation.id());
        entity.setProjectId(invitation.projectId());
        entity.setUserId(invitation.userId());
        entity.setInvitedByUserId(invitation.invitedByUserId());
        entity.setStatus(invitation.status().name());
        entity.setCreatedAt(invitation.createdAt());
        entity.setUpdatedAt(invitation.updatedAt());
        entity.setRespondedAt(invitation.respondedAt());
        try {
            return toInvitation(invitations.saveAndFlush(entity));
        } catch (DataIntegrityViolationException exception) {
            String details = exception.getMostSpecificCause().getMessage();
            if (details != null && (details.toLowerCase().contains("ux_invitations_pending_unique")
                    || details.toLowerCase().contains("ux_invitations_pending"))) {
                throw dev.platform.board.domain.aggregate.ProjectRuleViolation.conflict(
                        "INVITATION_ALREADY_PENDING", "A pending invitation already exists for this account.");
            }
            throw exception;
        }
    }

    @Override
    public Optional<Invitation> findInvitation(Long invitationId) {
        return invitations.findById(invitationId).map(this::toInvitation);
    }

    @Override
    public Optional<Invitation> findInvitationForUpdate(Long invitationId) {
        return invitations.findForUpdate(invitationId).map(this::toInvitation);
    }

    @Override
    public Optional<Invitation> findInvitation(Long invitationId, Long projectId) {
        return invitations.findByIdAndProjectId(invitationId, projectId).map(this::toInvitation);
    }

    @Override
    public boolean hasPendingInvitation(Long projectId, UUID userId) {
        return invitations.existsByProjectIdAndUserIdAndStatus(
                projectId, userId, Invitation.Status.PENDING.name());
    }

    @Override
    public ProjectPage<Invitation> listProjectInvitations(Long projectId, String status, int page, int size) {
        Page<InvitationEntity> result = status == null
                ? invitations.findByProjectId(projectId, PageRequest.of(page, size))
                : invitations.findByProjectIdAndStatus(projectId, status, PageRequest.of(page, size));
        return invitationPage(result);
    }

    @Override
    public ProjectPage<InvitationInboxItem> listUserInvitations(UUID userId, String status, int page, int size) {
        Page<InvitationEntity> result = status == null
                ? invitations.findByUserId(userId, PageRequest.of(page, size))
                : invitations.findByUserIdAndStatus(userId, status, PageRequest.of(page, size));
        List<InvitationInboxItem> items = result.getContent().stream()
                .map(invitation -> new InvitationInboxItem(toInvitation(invitation),
                        projects.findById(invitation.getProjectId()).map(ProjectEntity::getName).orElse("")))
                .toList();
        return new ProjectPage<>(items, result.getNumber(), result.getSize(), result.getTotalElements());
    }

    @Override
    public List<Long> listBoardIds(Long projectId) {
        return boards.findIdsByProjectId(projectId);
    }

    @Override
    public Optional<SnapshotSyncJobView> findSyncJobByOperationId(UUID operationId) {
        return syncJobRepository.findByOperationId(operationId)
                .map(e -> new SnapshotSyncJobView(
                        e.getOperationId(), e.getProjectId(), e.getUserId(), e.getStatus()));
    }

    @Override
    public boolean isActiveOwner(Long projectId, UUID userId) {
        return findMembership(projectId, userId)          // method đọc membership (không khóa) bạn đang có
                .filter(ProjectMembership::isActive)
                .map(m -> m.role() == ProjectMembership.Role.OWNER)
                .orElse(false);
    }

    private Project toProject(ProjectEntity entity) {
        return new Project(entity.getId(), entity.getName(), Project.Status.valueOf(entity.getStatus()),
                entity.getCreatedByUserId(), entity.getCreatedAt(), entity.getUpdatedAt());
    }

    private ProjectMembership toMembership(ProjectMembershipEntity entity) {
        return new ProjectMembership(entity.getId().getProjectId(), entity.getId().getUserId(),
                ProjectMembership.Role.valueOf(entity.getRole()),
                ProjectMembership.Status.valueOf(entity.getStatus()), entity.getJoinedAt(),
                entity.getRemovedAt(), entity.getUpdatedAt());
    }

    private MembershipVersion toVersion(ProjectMembershipVersionEntity entity) {
        return new MembershipVersion(entity.getId().getProjectId(), entity.getId().getUserId(),
                entity.getMembershipVersion(), entity.getAuthorizationVersion());
    }

    private Invitation toInvitation(InvitationEntity entity) {
        return new Invitation(entity.getId(), entity.getProjectId(), entity.getUserId(),
                entity.getInvitedByUserId(), Invitation.Status.valueOf(entity.getStatus()),
                entity.getCreatedAt(), entity.getUpdatedAt(), entity.getRespondedAt());
    }

    private ProjectPage<Project> projectPage(Page<ProjectEntity> page) {
        return new ProjectPage<>(page.getContent().stream().map(this::toProject).toList(),
                page.getNumber(), page.getSize(), page.getTotalElements());
    }

    private ProjectPage<ProjectMembership> membershipPage(Page<ProjectMembershipEntity> page) {
        return new ProjectPage<>(page.getContent().stream().map(this::toMembership).toList(),
                page.getNumber(), page.getSize(), page.getTotalElements());
    }

    private ProjectPage<Invitation> invitationPage(Page<InvitationEntity> page) {
        return new ProjectPage<>(page.getContent().stream().map(this::toInvitation).toList(),
                page.getNumber(), page.getSize(), page.getTotalElements());
    }
}
