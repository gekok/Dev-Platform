package dev.platform.contracts.event;

import java.util.UUID;

public record UserStatusChanged(UUID userId, String status, long authVersion) {
}
