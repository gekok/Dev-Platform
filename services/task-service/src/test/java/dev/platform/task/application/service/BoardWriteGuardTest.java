package dev.platform.task.application.service;

import dev.platform.task.application.port.out.BoardStateReaderPort;
import dev.platform.task.application.port.out.CardBoardLookupPort;
import dev.platform.task.application.query.BoardStateSnapshot;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class BoardWriteGuardTest {
    private final BoardStateReaderPort boardStates = mock(BoardStateReaderPort.class);
    private final CardBoardLookupPort cards = mock(CardBoardLookupPort.class);
    private final BoardWriteGuard guard = new BoardWriteGuard(boardStates, cards);

    @Test
    void allowsOnlyColumnsOnActiveBoardAndProject() {
        when(boardStates.getBoardState(9L)).thenReturn(snapshot("ACTIVE", "ACTIVE"));

        guard.requireWritable(5L, 9L, 2L);

        verify(boardStates).getBoardState(9L);
    }

    @Test
    void rejectsClosedBoardOrProjectAndInvalidColumn() {
        when(boardStates.getBoardState(9L)).thenReturn(snapshot("COMPLETED", "ACTIVE"));
        BoardNotWritableException boardClosed = assertThrows(BoardNotWritableException.class,
                () -> guard.requireWritable(5L, 9L, 2L));
        assertEquals("BOARD_NOT_ACTIVE", boardClosed.code());

        when(boardStates.getBoardState(9L)).thenReturn(snapshot("ACTIVE", "COMPLETED"));
        BoardNotWritableException projectClosed = assertThrows(BoardNotWritableException.class,
                () -> guard.requireWritable(5L, 9L, 2L));
        assertEquals("PROJECT_NOT_ACTIVE", projectClosed.code());

        when(boardStates.getBoardState(9L)).thenReturn(snapshot("ACTIVE", "ACTIVE"));
        assertThrows(InvalidBoardTargetException.class,
                () -> guard.requireWritable(5L, 9L, 99L));
    }

    @Test
    void moveResolvesCardBoardBeforeCheckingDestinationColumn() {
        when(cards.findBoardId(5L, 10L)).thenReturn(Optional.of(9L));
        when(boardStates.getBoardState(9L)).thenReturn(snapshot("ACTIVE", "ACTIVE"));

        guard.requireWritableForCard(5L, 10L, 2L);

        verify(cards).findBoardId(5L, 10L);
        verify(boardStates).getBoardState(9L);
    }

    private BoardStateSnapshot snapshot(String boardStatus, String projectStatus) {
        return new BoardStateSnapshot(9L, 5L, boardStatus, projectStatus,
                List.of(new BoardStateSnapshot.ColumnSnapshot(2L, "Doing", 0)));
    }
}
