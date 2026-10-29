package dev.platform.contracts.event;

import java.util.Set;
import java.util.UUID;

public record UserSystemRolesChanged(UUID userId, Set<String> systemRoles, long authVersion) {
}
