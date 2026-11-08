package dev.platform.board.domain.aggregate;

import java.util.UUID;

public record MembershipVersion(
        Long projectId,
        UUID userId,
        long membershipVersion,
        long authorizationVersion) {

    public MembershipVersion nextMembershipGeneration() {
        return new MembershipVersion(projectId, userId, membershipVersion + 1, authorizationVersion + 1);
    }

    public MembershipVersion nextAuthorizationVersion() {
        return new MembershipVersion(projectId, userId, membershipVersion, authorizationVersion + 1);
    }

    public static MembershipVersion initial(Long projectId, UUID userId) {
        return new MembershipVersion(projectId, userId, 1, 1);
    }
}
