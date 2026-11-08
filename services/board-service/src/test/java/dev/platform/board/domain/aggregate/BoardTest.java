package dev.platform.board.domain.aggregate;

import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BoardTest {
    private final OffsetDateTime now = OffsetDateTime.parse("2026-11-08T00:00:00Z");
    private final UUID creator = UUID.randomUUID();

    @Test
    void createsDefaultColumnsAndValidatesBoardKey() {
        Board board = Board.create(7L, "  Sprint 1 ", "sprint-1", creator, true, now);

        assertEquals("Sprint 1", board.name());
        assertEquals(List.of("Todo", "In Progress", "Done"),
                board.columns().stream().map(Board.Column::name).toList());
        assertThrows(ProjectRuleViolation.class,
                () -> Board.create(7L, "Sprint", "Sprint-1", creator, false, now));
    }

    @Test
    void insertingAndRemovingColumnsKeepsPositionsContiguous() {
        Board initial = boardWithColumns();
        Board inserted = initial.addColumn("Review", 1, now.plusMinutes(1));

        assertEquals(List.of(0, 1, 2, 3),
                inserted.columns().stream().map(Board.Column::position).toList());
        assertEquals("Review", inserted.columns().get(1).name());

        Board removed = inserted.removeColumn(2L, now.plusMinutes(2));
        assertEquals(List.of(0, 1, 2), removed.columns().stream().map(Board.Column::position).toList());
        assertEquals(java.util.Arrays.asList(1L, null, 3L),
                removed.columns().stream().map(Board.Column::id).toList());
    }

    @Test
    void reorderRequiresEveryColumnExactlyOnce() {
        Board board = boardWithColumns();
        Board reordered = board.reorder(List.of(3L, 1L, 2L), now.plusMinutes(1));

        assertEquals(List.of(3L, 1L, 2L), reordered.columns().stream().map(Board.Column::id).toList());
        assertThrows(ProjectRuleViolation.class,
                () -> board.reorder(List.of(1L, 1L, 3L), now));
        assertThrows(ProjectRuleViolation.class,
                () -> board.reorder(List.of(1L, 2L), now));
    }

    @Test
    void boardStatusCannotTransitionDirectlyBetweenTerminalStates() {
        Board board = boardWithColumns();
        Board completed = board.changeStatus(Board.Status.COMPLETED, now.plusMinutes(1));

        assertEquals(Board.Status.ACTIVE,
                completed.changeStatus(Board.Status.ACTIVE, now.plusMinutes(2)).status());
        assertEquals("INVALID_STATUS_TRANSITION",
                assertThrows(ProjectRuleViolation.class,
                        () -> completed.changeStatus(Board.Status.CANCELLED, now.plusMinutes(2))).code());
    }

    private Board boardWithColumns() {
        return new Board(9L, 7L, "Sprint", "sprint-1", Board.Status.ACTIVE, creator,
                now, now, List.of(
                        new Board.Column(1L, 9L, "Todo", 0),
                        new Board.Column(2L, 9L, "Doing", 1),
                        new Board.Column(3L, 9L, "Done", 2)));
    }
}
