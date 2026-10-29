package dev.platform.contracts.event;

import java.util.UUID;

public record MembershipChanged(
        Long projectId,
        UUID userId,
        String status,
        String role,
        long membershipVersion) {
}
