package dev.platform.contracts.event;

import java.util.UUID;

public record MembershipRemoved(Long projectId, UUID userId, long membershipVersion) {
}
