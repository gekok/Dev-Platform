package dev.platform.gateway.realtime;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;

import reactor.core.publisher.Mono;

@Component
public class RedisWebSocketTicketStore {

    private static final String KEY_PREFIX = "gateway:realtime:ws-ticket:";

    private final ReactiveStringRedisTemplate redis;

    public RedisWebSocketTicketStore(ReactiveStringRedisTemplate redis) {
        this.redis = redis;
    }

    public Mono<Void> save(String ticket, TicketClaims claims, Duration ttl) {
        String value = claims.accountId() + ":" + claims.boardId();
        return redis.opsForValue().set(KEY_PREFIX + ticket, value, ttl)
                .filter(Boolean.TRUE::equals)
                .switchIfEmpty(Mono.error(new IllegalStateException("WebSocket ticket was not stored")))
                .then();
    }

    public Mono<Optional<TicketClaims>> consume(String ticket) {
        return redis.opsForValue().getAndDelete(KEY_PREFIX + ticket)
                .map(RedisWebSocketTicketStore::parse)
                .map(Optional::of)
                .defaultIfEmpty(Optional.empty());
    }

    private static TicketClaims parse(String value) {
        int separator = value == null ? -1 : value.indexOf(':');
        if (separator < 1 || separator == value.length() - 1) {
            throw new IllegalStateException("Stored WebSocket ticket is malformed");
        }

        try {
            UUID accountId = UUID.fromString(value.substring(0, separator));
            long boardId = Long.parseLong(value.substring(separator + 1));
            if (boardId < 1) {
                throw new IllegalStateException("Stored WebSocket ticket has an invalid Board ID");
            }
            return new TicketClaims(accountId, boardId);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("Stored WebSocket ticket is malformed", exception);
        }
    }

    public record TicketClaims(UUID accountId, long boardId) {
    }
}
