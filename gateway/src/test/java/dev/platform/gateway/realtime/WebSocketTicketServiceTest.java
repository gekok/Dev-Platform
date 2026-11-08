package dev.platform.gateway.realtime;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

import java.time.Duration;

import org.junit.jupiter.api.Test;

class WebSocketTicketServiceTest {

    @Test
    void rejectsTicketTtlBelowOneSecond() {
        RedisWebSocketTicketStore store = mock(RedisWebSocketTicketStore.class);

        assertThrows(IllegalArgumentException.class,
                () -> new WebSocketTicketService(store, Duration.ofMillis(999)));
    }

    @Test
    void acceptsTicketTtlOfOneSecond() {
        RedisWebSocketTicketStore store = mock(RedisWebSocketTicketStore.class);

        assertDoesNotThrow(() -> new WebSocketTicketService(store, Duration.ofSeconds(1)));
    }
}
