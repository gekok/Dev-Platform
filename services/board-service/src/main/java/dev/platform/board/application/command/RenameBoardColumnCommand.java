package dev.platform.board.application.command;

import java.util.UUID;

public record RenameBoardColumnCommand(Long columnId, String name, UUID actorId) {
}
