package dev.platform.board.application.query;

import dev.platform.board.domain.aggregate.Project;

public record ProjectView(Project project, String myRole) {
}
