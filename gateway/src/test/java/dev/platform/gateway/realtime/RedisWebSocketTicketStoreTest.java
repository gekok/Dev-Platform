package dev.platform.gateway.realtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;

import reactor.core.publisher.Mono;

class RedisWebSocketTicketStoreTest {

    @SuppressWarnings("unchecked")
    private final ReactiveValueOperations<String, String> values =
            mock(ReactiveValueOperations.class);
    private final ReactiveStringRedisTemplate redis = mock(ReactiveStringRedisTemplate.class);
    private final RedisWebSocketTicketStore store = new RedisWebSocketTicketStore(redis);

    RedisWebSocketTicketStoreTest() {
        when(redis.opsForValue()).thenReturn(values);
    }

    @Test
    void storesTicketWithTtlAndConsumesItAtomically() {
        String ticket = "opaque-ticket";
        String key = "gateway:realtime:ws-ticket:" + ticket;
        UUID accountId = UUID.fromString("11111111-1111-4111-8111-111111111111");
        Duration ttl = Duration.ofSeconds(30);
        when(values.set(key, accountId + ":42", ttl)).thenReturn(Mono.just(true));
        when(values.getAndDelete(key)).thenReturn(Mono.just(accountId + ":42"));

        store.save(ticket, new RedisWebSocketTicketStore.TicketClaims(accountId, 42L), ttl).block();
        Optional<RedisWebSocketTicketStore.TicketClaims> consumed = store.consume(ticket).block();

        assertEquals(Optional.of(new RedisWebSocketTicketStore.TicketClaims(accountId, 42L)), consumed);
        verify(values).set(key, accountId + ":42", ttl);
        verify(values).getAndDelete(key);
    }

    @Test
    void reportsTicketAsMissingWhenRedisGetAndDeleteReturnsEmpty() {
        String key = "gateway:realtime:ws-ticket:expired-ticket";
        when(values.getAndDelete(key)).thenReturn(Mono.empty());

        Optional<RedisWebSocketTicketStore.TicketClaims> consumed = store.consume("expired-ticket").block();

        assertTrue(consumed.isEmpty());
    }
}
