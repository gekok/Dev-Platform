package dev.platform.board.api.dto;

import dev.platform.board.application.query.MemberAccess;

public record InternalMembershipResponse(
        String role,
        String status,
        long membershipVersion,
        String projectStatus) {
    public static InternalMembershipResponse from(MemberAccess access) {
        return new InternalMembershipResponse(access.role(), access.active() ? "ACTIVE" : "REMOVED",
                access.membershipVersion(), access.projectStatus());
    }
}
