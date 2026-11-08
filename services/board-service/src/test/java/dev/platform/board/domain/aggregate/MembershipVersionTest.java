package dev.platform.board.domain.aggregate;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MembershipVersionTest {
    @Test
    void membershipGenerationAlsoAdvancesAuthorizationVersion() {
        MembershipVersion current = new MembershipVersion(1L, UUID.randomUUID(), 3, 5);

        MembershipVersion next = current.nextMembershipGeneration();

        assertEquals(4, next.membershipVersion());
        assertEquals(6, next.authorizationVersion());
    }

    @Test
    void roleChangesCanAdvanceOnlyAuthorizationVersion() {
        MembershipVersion current = new MembershipVersion(1L, UUID.randomUUID(), 3, 5);

        MembershipVersion next = current.nextAuthorizationVersion();

        assertEquals(3, next.membershipVersion());
        assertEquals(6, next.authorizationVersion());
    }
}
