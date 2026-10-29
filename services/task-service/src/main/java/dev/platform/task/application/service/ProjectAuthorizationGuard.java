package dev.platform.task.application.service;

import java.util.UUID;

import dev.platform.task.application.port.out.AuthorizationSnapshotReader;
import dev.platform.task.application.port.out.AuthorizationSnapshotReader.ProjectMembershipSnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ProjectAuthorizationGuard {

    private final AuthorizationSnapshotReader snapshots;

    public ProjectMembershipSnapshot requireProjectMember(Long projectId, UUID userId) {
        if (projectId == null || userId == null) {
            throw new ProjectAccessDeniedException("Project identity is required");
        }
        AuthorizationSnapshotReader.AccountSnapshot account = snapshots.getAccount(userId);
        if (!"ACTIVE".equals(account.status())) {
            throw new ProjectAccessDeniedException("Account is not active");
        }
        ProjectMembershipSnapshot membership = snapshots.getProjectMembership(projectId, userId);
        if (!"ACTIVE".equals(membership.status())) {
            throw new ProjectAccessDeniedException("Project membership is not active");
        }
        return membership;
    }

    public long requireActiveAssigneeMembership(Long projectId, UUID assigneeId) {
        return requireProjectMember(projectId, assigneeId).membershipVersion();
    }
}
