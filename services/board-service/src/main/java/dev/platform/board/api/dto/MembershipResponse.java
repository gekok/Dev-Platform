package dev.platform.board.api.dto;

import dev.platform.board.domain.aggregate.ProjectMembership;

import java.time.OffsetDateTime;
import java.util.UUID;

public record MembershipResponse(UUID userId, String role, OffsetDateTime joinedAt) {
    public static MembershipResponse from(ProjectMembership membership) {
        return new MembershipResponse(membership.userId(), membership.role().name(), membership.joinedAt());
    }
}
