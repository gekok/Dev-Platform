package dev.platform.task.application.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Optional;
import java.util.UUID;
import dev.platform.task.application.command.AssignCardCommand;
import dev.platform.task.application.exception.CardNotFoundException;
import dev.platform.task.application.port.out.AssignmentLockPort;
import dev.platform.task.application.port.out.SaveCardPort;
import dev.platform.task.domain.aggregate.CardAggregate;
import org.junit.jupiter.api.Test;

class AssignCardServiceTest {
    @Test
    void savesAssignmentWithMembershipGeneration() {
        var cards = mock(SaveCardPort.class);
        var lock = mock(AssignmentLockPort.class);
        var user = UUID.randomUUID();
        var actor = UUID.randomUUID();
        var card = new CardAggregate();
        when(cards.findForUpdate(1L, 2L)).thenReturn(Optional.of(card));
        when(cards.save(card)).thenReturn(card);
        var service = new AssignCardService(cards, lock);
        var result = service.assign(new AssignCardCommand(1L, 2L, user, 4L, actor));
        assertEquals(user, result.getAssigneeId());
        assertEquals(4L, result.getAssigneeMembershipVersion());
        var order = inOrder(lock, cards);
        order.verify(lock).lock(1L, user);
        order.verify(cards).findForUpdate(1L, 2L);
        order.verify(cards).save(card);
    }

    @Test
    void rejectsCardOutsideProject() {
        var cards = mock(SaveCardPort.class);
        when(cards.findForUpdate(1L, 2L)).thenReturn(Optional.empty());
        var service = new AssignCardService(cards, mock(AssignmentLockPort.class));
        assertThrows(CardNotFoundException.class,
                () -> service.assign(new AssignCardCommand(1L, 2L, null, null, UUID.randomUUID())));
        verify(cards, never()).save(any());
    }

    @Test
    void rejoinUpdatesGenerationEvenWhenUserIsUnchanged() {
        var cards = mock(SaveCardPort.class);
        var user = UUID.randomUUID();
        var card = new CardAggregate();
        card.setAssigneeId(user);
        card.setAssigneeMembershipVersion(1L);
        when(cards.findForUpdate(1L, 2L)).thenReturn(Optional.of(card));
        when(cards.save(card)).thenReturn(card);
        var service = new AssignCardService(cards, mock(AssignmentLockPort.class));
        var result = service.assign(new AssignCardCommand(1L, 2L, user, 2L, UUID.randomUUID()));
        assertEquals(2L, result.getAssigneeMembershipVersion());
        verify(cards).save(card);
    }

    @Test
    void sameGenerationDoesNotWriteAgain() {
        var cards = mock(SaveCardPort.class);
        var user = UUID.randomUUID();
        var card = new CardAggregate();
        card.setAssigneeId(user);
        card.setAssigneeMembershipVersion(2L);
        when(cards.findForUpdate(1L, 2L)).thenReturn(Optional.of(card));
        var service = new AssignCardService(cards, mock(AssignmentLockPort.class));
        assertSame(card, service.assign(new AssignCardCommand(1L, 2L, user, 2L, UUID.randomUUID())));
        verify(cards, never()).save(any());
    }

    @Test
    void unassignClearsGeneration() {
        var cards = mock(SaveCardPort.class);
        var card = new CardAggregate();
        card.setAssigneeId(UUID.randomUUID());
        card.setAssigneeMembershipVersion(2L);
        when(cards.findForUpdate(1L, 2L)).thenReturn(Optional.of(card));
        when(cards.save(card)).thenReturn(card);
        var service = new AssignCardService(cards, mock(AssignmentLockPort.class));
        var result = service.assign(new AssignCardCommand(1L, 2L, null, null, UUID.randomUUID()));
        assertNull(result.getAssigneeId());
        assertNull(result.getAssigneeMembershipVersion());
    }
}
