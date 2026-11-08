package dev.platform.board.application.command;

import java.util.UUID;

public record ProjectNameCommand(Long projectId, String name, UUID actorId) {
}
