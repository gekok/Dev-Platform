package dev.platform.gateway.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import dev.platform.gateway.realtime.RedisWebSocketTicketStore;
import dev.platform.gateway.realtime.WebSocketTicketService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilterChain;

import reactor.core.publisher.Mono;

class GatewayAuthenticationFilterWebSocketTest {

    private static final UUID ACCOUNT_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final String ALLOWED_ORIGIN = "http://localhost:5173";

    private final UserServicePublicKeyProvider publicKeyProvider = mock(UserServicePublicKeyProvider.class);
    private final RedisAccountSnapshotReader snapshotReader = mock(RedisAccountSnapshotReader.class);
    private final WebSocketTicketService tickets = mock(WebSocketTicketService.class);
    private final GatewayAuthenticationFilter filter = new GatewayAuthenticationFilter(
            publicKeyProvider,
            snapshotReader,
            tickets,
            "dev-platform-user-service",
            List.of(ALLOWED_ORIGIN));

    @Test
    void consumesTicketAndForwardsOnlyGatewayVerifiedIdentity() {
        when(tickets.consume("single-use-ticket")).thenReturn(Mono.just(Optional.of(
                new RedisWebSocketTicketStore.TicketClaims(ACCOUNT_ID, 42L))));
        MockServerWebExchange exchange = websocketExchange(
                "/ws/boards/42?ticket=single-use-ticket&cursor=abc",
                ALLOWED_ORIGIN,
                "X-User-Id", "spoofed-user",
                "X-User-Role", "SYSTEM_ADMIN",
                "X-Gateway-Signature", "spoofed-signature",
                HttpHeaders.AUTHORIZATION, "Bearer client-token");
        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();

        filter.filter(exchange, forwardedExchange(forwarded)).block();

        ServerWebExchange result = forwarded.get();
        assertEquals(ACCOUNT_ID.toString(), result.getRequest().getHeaders().getFirst("X-User-Id"));
        assertNull(result.getRequest().getHeaders().getFirst("X-User-Role"));
        assertNull(result.getRequest().getHeaders().getFirst("X-Gateway-Signature"));
        assertNull(result.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION));
        assertNull(result.getRequest().getHeaders().getFirst(HttpHeaders.ORIGIN));
        assertEquals("/ws/boards/42", result.getRequest().getURI().getPath());
        assertEquals("cursor=abc", result.getRequest().getURI().getRawQuery());
    }

    @Test
    void rejectsMissingOrConsumedTicket() {
        when(tickets.consume("already-used")).thenReturn(Mono.just(Optional.empty()));
        MockServerWebExchange exchange = websocketExchange(
                "/ws/boards/42?ticket=already-used", ALLOWED_ORIGIN);

        filter.filter(exchange, ignored -> Mono.error(new AssertionError("rejected request was forwarded"))).block();

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
    }

    @Test
    void rejectsTicketForDifferentBoard() {
        when(tickets.consume("ticket-for-board-41")).thenReturn(Mono.just(Optional.of(
                new RedisWebSocketTicketStore.TicketClaims(ACCOUNT_ID, 41L))));
        MockServerWebExchange exchange = websocketExchange(
                "/ws/boards/42?ticket=ticket-for-board-41", ALLOWED_ORIGIN);

        filter.filter(exchange, ignored -> Mono.error(new AssertionError("rejected request was forwarded"))).block();

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
    }

    @Test
    void rejectsUnapprovedOriginBeforeConsumingTicket() {
        MockServerWebExchange exchange = websocketExchange(
                "/ws/boards/42?ticket=single-use-ticket", "https://attacker.example");

        filter.filter(exchange, ignored -> Mono.error(new AssertionError("rejected request was forwarded"))).block();

        assertEquals(HttpStatus.FORBIDDEN, exchange.getResponse().getStatusCode());
        verify(tickets, never()).consume("single-use-ticket");
    }

    @Test
    void failsClosedWhenTicketStoreIsUnavailable() {
        when(tickets.consume("single-use-ticket"))
                .thenReturn(Mono.error(new IllegalStateException("Redis unavailable")));
        MockServerWebExchange exchange = websocketExchange(
                "/ws/boards/42?ticket=single-use-ticket", ALLOWED_ORIGIN);

        filter.filter(exchange, ignored -> Mono.error(new AssertionError("unavailable ticket store authorized"))).block();

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, exchange.getResponse().getStatusCode());
    }

    @Test
    void rejectsUnsupportedWebSocketPath() {
        MockServerWebExchange exchange = websocketExchange("/ws/notifications?ticket=single-use-ticket", ALLOWED_ORIGIN);

        filter.filter(exchange, ignored -> Mono.error(new AssertionError("invalid path was forwarded"))).block();

        assertEquals(HttpStatus.BAD_REQUEST, exchange.getResponse().getStatusCode());
    }

    private static MockServerWebExchange websocketExchange(String uri, String origin, String... headers) {
        MockServerHttpRequest.BaseBuilder<?> request = MockServerHttpRequest.get(uri).header("Origin", origin);
        for (int i = 0; i < headers.length; i += 2) {
            request.header(headers[i], headers[i + 1]);
        }
        return MockServerWebExchange.from(request.build());
    }

    private static WebFilterChain forwardedExchange(AtomicReference<ServerWebExchange> forwarded) {
        return exchange -> {
            forwarded.set(exchange);
            return Mono.empty();
        };
    }
}
