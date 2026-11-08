package dev.platform.task.infrastructure.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import dev.platform.task.application.port.out.BoardStateReaderPort;
import dev.platform.task.application.query.BoardStateSnapshot;
import dev.platform.task.application.service.BoardStateUnavailableException;
import dev.platform.task.application.service.InvalidBoardTargetException;
import org.junit.jupiter.api.Test;

class BoardProjectLookupAdapterTest {

    private final BoardStateReaderPort boardStateReader = mock(BoardStateReaderPort.class);
    private final BoardProjectLookupAdapter adapter = new BoardProjectLookupAdapter(boardStateReader);

    @Test
    void returnsOwningProjectFromPersistedBoardState() {
        when(boardStateReader.getBoardState(42L)).thenReturn(new BoardStateSnapshot(
                42L,
                100L,
                "ACTIVE",
                "ACTIVE",
                List.of()));

        assertEquals(Optional.of(100L), adapter.findProjectId(42L));
    }

    @Test
    void returnsEmptyWhenBoardStateReaderReportsMissingBoard() {
        when(boardStateReader.getBoardState(404L)).thenThrow(new InvalidBoardTargetException());

        assertEquals(Optional.empty(), adapter.findProjectId(404L));
    }

    @Test
    void propagatesUnavailableBoardStateInsteadOfAuthorizing() {
        BoardStateUnavailableException failure =
                new BoardStateUnavailableException("Board Service unavailable", null);
        when(boardStateReader.getBoardState(42L)).thenThrow(failure);

        assertThrows(BoardStateUnavailableException.class, () -> adapter.findProjectId(42L));
    }
}
