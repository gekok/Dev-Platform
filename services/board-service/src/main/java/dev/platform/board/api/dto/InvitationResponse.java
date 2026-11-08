package dev.platform.board.api.dto;

import dev.platform.board.domain.aggregate.Invitation;

import java.time.OffsetDateTime;
import java.util.UUID;

public record InvitationResponse(
        Long id,
        Long projectId,
        String projectName,
        UUID userId,
        UUID invitedByUserId,
        String status,
        OffsetDateTime createdAt,
        OffsetDateTime respondedAt) {

    public static InvitationResponse from(Invitation invitation) {
        return from(invitation, null);
    }

    public static InvitationResponse from(Invitation invitation, String projectName) {
        return new InvitationResponse(invitation.id(), invitation.projectId(), projectName,
                invitation.userId(), invitation.invitedByUserId(), invitation.status().name(),
                invitation.createdAt(), invitation.respondedAt());
    }
}
