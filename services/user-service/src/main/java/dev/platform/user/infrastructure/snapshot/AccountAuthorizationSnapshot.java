package dev.platform.user.infrastructure.snapshot;

import java.util.Set;
import java.util.UUID;

public record AccountAuthorizationSnapshot(
        UUID accountId,
        String status,
        Set<String> systemRoles,
        long authVersion) {
}
