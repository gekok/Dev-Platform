package dev.platform.board.api.dto;

import dev.platform.board.domain.aggregate.Project;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ProjectResponse(
        Long id,
        String name,
        String status,
        UUID createdByUserId,
        String myRole,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {

    public static ProjectResponse from(Project project, String myRole) {
        return new ProjectResponse(project.id(), project.name(), project.status().name(),
                project.createdByUserId(), myRole, project.createdAt(), project.updatedAt());
    }
}
