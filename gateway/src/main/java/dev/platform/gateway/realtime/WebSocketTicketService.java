package dev.platform.gateway.realtime;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import reactor.core.publisher.Mono;

@Service
public class WebSocketTicketService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int TICKET_BYTES = 32;

    private final RedisWebSocketTicketStore store;
    private final Duration ticketTtl;

    public WebSocketTicketService(
            RedisWebSocketTicketStore store,
            @Value("${gateway.realtime.websocket.ticket-ttl:PT30S}") Duration ticketTtl) {
        if (ticketTtl == null || ticketTtl.compareTo(Duration.ofSeconds(1)) < 0) {
            throw new IllegalArgumentException("WebSocket ticket TTL must be at least one second");
        }
        this.store = store;
        this.ticketTtl = ticketTtl;
    }

    public Mono<IssuedTicket> issue(UUID accountId, long boardId) {
        if (accountId == null || boardId < 1) {
            return Mono.error(new IllegalArgumentException("Account and positive Board ID are required"));
        }

        byte[] randomBytes = new byte[TICKET_BYTES];
        RANDOM.nextBytes(randomBytes);
        String ticket = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
        RedisWebSocketTicketStore.TicketClaims claims =
                new RedisWebSocketTicketStore.TicketClaims(accountId, boardId);

        return store.save(ticket, claims, ticketTtl)
                .thenReturn(new IssuedTicket(ticket, ticketTtl.toSeconds()));
    }

    public Mono<Optional<RedisWebSocketTicketStore.TicketClaims>> consume(String ticket) {
        if (ticket == null || ticket.isBlank() || ticket.length() > 128) {
            return Mono.just(Optional.empty());
        }
        return store.consume(ticket);
    }

    public record IssuedTicket(String ticket, long expiresInSeconds) {
    }
}
