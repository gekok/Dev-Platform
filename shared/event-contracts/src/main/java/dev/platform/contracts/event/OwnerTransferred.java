package dev.platform.contracts.event;

import java.util.UUID;

public record OwnerTransferred(Long projectId, UUID fromUserId, UUID toUserId) {
}
