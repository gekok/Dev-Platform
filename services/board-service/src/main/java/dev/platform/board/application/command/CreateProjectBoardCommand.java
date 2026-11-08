package dev.platform.board.application.command;

import java.util.UUID;

public record CreateProjectBoardCommand(
        Long projectId,
        String name,
        String boardKey,
        boolean createDefaultColumns,
        UUID actorId) {
}
