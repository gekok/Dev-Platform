package dev.platform.task.application.service;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class CardCursorTest {
    @Test
    void cursorBindsPositionAndScope() {
        String cursor = CardCursor.encode(1L, 2L, 3L, 10, 9L);
        assertEquals(9L, CardCursor.decode(cursor, 1L, 2L, 3L).id());
        assertThrows(IllegalArgumentException.class, () -> CardCursor.decode(cursor, 1L, 2L, 4L));
        assertThrows(IllegalArgumentException.class, () -> CardCursor.decode(cursor, 2L, 2L, 3L));
    }
}
