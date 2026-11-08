package dev.platform.board.application.command;

import java.util.UUID;

public record TransferOwnershipCommand(Long projectId, UUID newOwnerId, UUID actorId) {
}
