package dev.platform.contracts.event;

import java.util.UUID;

public record MemberAdded(Long projectId, UUID userId, long membershipVersion) {
}
