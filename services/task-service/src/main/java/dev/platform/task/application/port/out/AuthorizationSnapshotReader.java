package dev.platform.task.application.port.out;

import java.util.Set;
import java.util.UUID;

public interface AuthorizationSnapshotReader {

    AccountSnapshot getAccount(UUID userId);

    ProjectMembershipSnapshot getProjectMembership(Long projectId, UUID userId);

    record AccountSnapshot(UUID accountId, String status, Set<String> systemRoles, long authVersion) {
    }

    record ProjectMembershipSnapshot(
            Long projectId, UUID userId, String status, String role,
            long membershipVersion, long authorizationVersion) {
    }
}
