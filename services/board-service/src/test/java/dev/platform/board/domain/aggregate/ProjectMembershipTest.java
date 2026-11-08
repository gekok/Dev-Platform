package dev.platform.board.domain.aggregate;

import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ProjectMembershipTest {
    private final UUID userId = UUID.randomUUID();
    private final OffsetDateTime joinedAt = OffsetDateTime.parse("2026-11-07T00:00:00Z");

    @Test
    void removalRetainsMembershipAndReactivationCreatesNewMembershipPeriod() {
        ProjectMembership active = new ProjectMembership(1L, userId, ProjectMembership.Role.MEMBER,
                ProjectMembership.Status.ACTIVE, joinedAt, null, joinedAt);
        OffsetDateTime removedAt = joinedAt.plusDays(2);
        ProjectMembership removed = active.remove(removedAt);
        ProjectMembership reactivated = removed.reactivateAsMember(removedAt.plusDays(1));

        assertEquals(ProjectMembership.Status.REMOVED, removed.status());
        assertEquals(removedAt, removed.removedAt());
        assertEquals(ProjectMembership.Status.ACTIVE, reactivated.status());
        assertEquals(removedAt.plusDays(1), reactivated.joinedAt());
        assertNull(reactivated.removedAt());
    }
}
