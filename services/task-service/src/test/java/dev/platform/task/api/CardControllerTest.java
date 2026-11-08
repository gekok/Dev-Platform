package dev.platform.task.api;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import dev.platform.task.application.port.in.*;
import dev.platform.task.application.service.*;
import dev.platform.task.application.exception.CardOperationUnavailableException;
import dev.platform.task.domain.aggregate.CardAggregate;
import dev.platform.task.api.dto.CreateCardRequest;

class CardControllerTest {
    @Test
    void listUsesKeysetAndLimitsPage() {
        GetCardsPort reader = mock(GetCardsPort.class);
        ProjectAuthorizationGuard guard = mock(ProjectAuthorizationGuard.class);
        CardController controller = new CardController(mock(CreateCardPort.class), reader,
                mock(GetCardPort.class), mock(MoveCardPort.class), mock(AssignCardPort.class),
                mock(AddCommentPort.class), guard);
        ReflectionTestUtils.setField(controller, "trustedGatewayIdentityEnabled", true);
        CardAggregate first = new CardAggregate(); first.setId(7L); first.setPosition(3);
        CardAggregate extra = new CardAggregate(); extra.setId(6L); extra.setPosition(3);
        when(reader.getByBoard(1L, 2L, 3L, null, null, 2)).thenReturn(List.of(first, extra));
        UUID actor = UUID.randomUUID();
        var page = controller.listCards(1L, 2L, 3L, 1, null, actor).getBody();
        assertEquals(1, page.items().size());
        assertEquals(7L, CardCursor.decode(page.nextCursor(), 1L, 2L, 3L).id());
        assertThrows(IllegalArgumentException.class, () -> controller.listCards(1L, 2L, 3L, 21, null, actor));
        verify(guard, times(2)).requireProjectMember(1L, actor);
    }

    @Test
    void createFailsClosedBeforeTrustedGatewayIdentity() {
        CreateCardPort writer = mock(CreateCardPort.class);
        CardController controller = new CardController(writer, mock(GetCardsPort.class), mock(GetCardPort.class),
                mock(MoveCardPort.class), mock(AssignCardPort.class), mock(AddCommentPort.class),
                mock(ProjectAuthorizationGuard.class));
        when(writer.create(any())).thenThrow(new CardOperationUnavailableException());
        assertThrows(CardOperationUnavailableException.class,
                () -> controller.createCard(1L, 2L, new CreateCardRequest(3L, "Card", null, null, null), UUID.randomUUID()));
        verify(writer, never()).create(any());
    }
}
