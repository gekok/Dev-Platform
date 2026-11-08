package dev.platform.board.application.query;

import dev.platform.board.domain.aggregate.Invitation;

public record InvitationInboxItem(Invitation invitation, String projectName) {
}
