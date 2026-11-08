package dev.platform.board.application.command;

import java.util.UUID;

public record CreateProjectCommand(String name, UUID actorId) {
}
