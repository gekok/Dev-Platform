package dev.platform.board.domain.aggregate;

import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class InvitationTest {
    @Test
    void cancellingInvitationDoesNotMarkItAsRespondedByInvitee() {
        OffsetDateTime created = OffsetDateTime.parse("2026-11-07T00:00:00Z");
        Invitation invitation = new Invitation(1L, 2L, UUID.randomUUID(), UUID.randomUUID(),
                Invitation.Status.PENDING, created, created, null);

        Invitation cancelled = invitation.respond(Invitation.Status.CANCELLED, created.plusHours(1));

        assertNull(cancelled.respondedAt());
        assertNotNull(cancelled.updatedAt());
    }
}
