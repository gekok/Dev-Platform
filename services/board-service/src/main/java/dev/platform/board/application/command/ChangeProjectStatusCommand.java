package dev.platform.board.application.command;

import dev.platform.board.domain.aggregate.Project;

import java.util.UUID;

public record ChangeProjectStatusCommand(Long projectId, Project.Status status, UUID actorId) {
}
