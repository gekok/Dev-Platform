package dev.platform.gateway.security;

import java.security.interfaces.RSAPublicKey;
import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import org.springframework.web.server.ServerWebExchange;
import dev.platform.gateway.realtime.RedisWebSocketTicketStore;
import dev.platform.gateway.realtime.WebSocketTicketService;

import reactor.core.publisher.Mono;

@Component
public class GatewayAuthenticationFilter implements WebFilter, Ordered {

    private static final String USER_ID_HEADER = "X-User-Id";
    private static final String LOGIN_PATH = "/api/v1/auth/login";
    private static final String PUBLIC_KEY_PATH = "/api/v1/auth/public-key";
    private static final String HEALTH_PATH = "/actuator/health";

    private final UserServicePublicKeyProvider publicKeyProvider;
    private final RedisAccountSnapshotReader snapshotReader;
    private final WebSocketTicketService webSocketTickets;
    private final String issuer;
    private final List<String> allowedWebSocketOrigins;

    public GatewayAuthenticationFilter(
            UserServicePublicKeyProvider publicKeyProvider,
            RedisAccountSnapshotReader snapshotReader,
            WebSocketTicketService webSocketTickets,
            @Value("${app.jwt.issuer:dev-platform-user-service}") String issuer,
            @Value("${gateway.realtime.websocket.allowed-origins:http://localhost:5173}")
            List<String> allowedWebSocketOrigins) {
        this.publicKeyProvider = publicKeyProvider;
        this.snapshotReader = snapshotReader;
        this.webSocketTickets = webSocketTickets;
        this.issuer = issuer;
        this.allowedWebSocketOrigins = allowedWebSocketOrigins;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        if (isWebSocketPath(exchange)) {
            return authorizeWebSocketTicket(exchange, chain);
        }
        if (isPublic(exchange)) {
            return chain.filter(withoutClientIdentity(exchange));
        }

        String token = bearerToken(exchange.getRequest().getHeaders());
        if (token == null) {
            return respond(exchange, HttpStatus.UNAUTHORIZED);
        }

        return authorize(token)
                .flatMap(result -> {
                    if (result.rejectionStatus() != null) {
                        return respond(exchange, result.rejectionStatus());
                    }
                    return chain.filter(withVerifiedIdentity(exchange, result.accountId()));
                });
    }

    private Mono<Void> authorizeWebSocketTicket(ServerWebExchange exchange, WebFilterChain chain) {
        if (!HttpMethod.GET.equals(exchange.getRequest().getMethod())) {
            return respond(exchange, HttpStatus.METHOD_NOT_ALLOWED);
        }

        Long boardId = boardIdFromWebSocketPath(exchange.getRequest().getURI().getPath());
        if (boardId == null) {
            return respond(exchange, HttpStatus.BAD_REQUEST);
        }
        if (!isAllowedWebSocketOrigin(exchange.getRequest().getHeaders())) {
            return respond(exchange, HttpStatus.FORBIDDEN);
        }

        List<String> suppliedTickets = exchange.getRequest().getQueryParams().get("ticket");
        if (suppliedTickets == null || suppliedTickets.size() != 1
                || suppliedTickets.getFirst().isBlank()) {
            return respond(exchange, HttpStatus.UNAUTHORIZED);
        }

        return webSocketTickets.consume(suppliedTickets.getFirst())
                .map(claims -> websocketAuthorization(claims, boardId))
                .onErrorResume(error -> Mono.just(
                        AuthorizationResult.rejected(HttpStatus.SERVICE_UNAVAILABLE)))
                .flatMap(result -> {
                    if (result.rejectionStatus() != null) {
                        return respond(exchange, result.rejectionStatus());
                    }
                    return chain.filter(withVerifiedWebSocketIdentity(exchange, result.accountId()));
                });
    }

    private static AuthorizationResult websocketAuthorization(
            Optional<RedisWebSocketTicketStore.TicketClaims> claims,
            long requestedBoardId) {
        if (claims.isEmpty() || claims.get().boardId() != requestedBoardId) {
            return AuthorizationResult.rejected(HttpStatus.UNAUTHORIZED);
        }
        return AuthorizationResult.allowed(claims.get().accountId());
    }

    private boolean isAllowedWebSocketOrigin(HttpHeaders headers) {
        List<String> origins = headers.get("Origin");
        if (origins == null || origins.size() != 1 || origins.getFirst().isBlank()) {
            return false;
        }
        String origin = origins.getFirst().trim();
        return allowedWebSocketOrigins.stream()
                .map(String::trim)
                .anyMatch(origin::equals);
    }

    private Mono<AuthorizationResult> authorize(String token) {
        return publicKeyProvider.getPublicKey()
                .flatMap(publicKey -> {
                    final UUID accountId;
                    try {
                        accountId = authenticate(token, publicKey);
                    } catch (JWTVerificationException | IllegalArgumentException | NullPointerException exception) {
                        return Mono.just(AuthorizationResult.rejected(HttpStatus.UNAUTHORIZED));
                    }
                    return snapshotReader.read(accountId)
                            .map(snapshot -> "ACTIVE".equals(snapshot.status())
                                    ? AuthorizationResult.allowed(accountId)
                                    : AuthorizationResult.rejected(HttpStatus.FORBIDDEN));
                })
                .onErrorResume(error -> Mono.just(
                        AuthorizationResult.rejected(HttpStatus.SERVICE_UNAVAILABLE)));
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    private UUID authenticate(String token, RSAPublicKey publicKey) {
        DecodedJWT jwt = JWT.require(Algorithm.RSA256(publicKey, null))
                .withIssuer(issuer)
                .build()
                .verify(token);
        if (jwt.getIssuedAt() == null || jwt.getExpiresAt() == null) {
            throw new IllegalArgumentException("JWT is missing a required time claim");
        }

        String subject = jwt.getSubject();
        UUID accountId = UUID.fromString(subject);
        if (!accountId.toString().equalsIgnoreCase(subject)) {
            throw new IllegalArgumentException("JWT subject is not a canonical UUID");
        }
        return accountId;
    }

    private static boolean isPublic(ServerWebExchange exchange) {
        HttpMethod method = exchange.getRequest().getMethod();
        String path = exchange.getRequest().getURI().getPath();
        return (HttpMethod.POST.equals(method) && LOGIN_PATH.equals(path))
                || (HttpMethod.GET.equals(method) && PUBLIC_KEY_PATH.equals(path))
                || (HttpMethod.GET.equals(method) && HEALTH_PATH.equals(path));
    }

    private static boolean isWebSocketPath(ServerWebExchange exchange) {
        String path = exchange.getRequest().getURI().getPath();
        return "/ws".equals(path) || (path != null && path.startsWith("/ws/"));
    }

    private static Long boardIdFromWebSocketPath(String path) {
        if (path == null) {
            return null;
        }
        String[] parts = path.split("/");
        if (parts.length != 4 || !"ws".equals(parts[1]) || !"boards".equals(parts[2])) {
            return null;
        }
        try {
            long boardId = Long.parseLong(parts[3]);
            return boardId > 0 ? boardId : null;
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static String bearerToken(HttpHeaders headers) {
        String authorization = headers.getFirst(HttpHeaders.AUTHORIZATION);
        if (authorization == null || !authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return null;
        }
        String token = authorization.substring(7).trim();
        return token.isEmpty() ? null : token;
    }

    private static ServerWebExchange withoutClientIdentity(ServerWebExchange exchange) {
        ServerHttpRequest request = exchange.getRequest().mutate()
                .headers(headers -> headers.remove(USER_ID_HEADER))
                .build();
        return exchange.mutate().request(request).build();
    }

    private static ServerWebExchange withVerifiedIdentity(ServerWebExchange exchange, UUID accountId) {
        ServerHttpRequest request = exchange.getRequest().mutate()
                .headers(headers -> {
                    headers.remove(USER_ID_HEADER);
                    headers.set(USER_ID_HEADER, accountId.toString());
                })
                .build();
        return exchange.mutate().request(request).build();
    }

    private static ServerWebExchange withVerifiedWebSocketIdentity(
            ServerWebExchange exchange,
            UUID accountId) {
        URI sanitizedUri = UriComponentsBuilder.fromUri(exchange.getRequest().getURI())
                .replaceQueryParam("ticket")
                .build(true)
                .toUri();
        ServerHttpRequest request = exchange.getRequest().mutate()
                .uri(sanitizedUri)
                .headers(headers -> {
                    List<String> untrusted = headers.headerSet().stream()
                            .map(entry -> entry.getKey())
                            .filter(GatewayAuthenticationFilter::isClientSecurityHeader)
                            .toList();
                    untrusted.forEach(headers::remove);
                    headers.set(USER_ID_HEADER, accountId.toString());
                })
                .build();
        return exchange.mutate().request(request).build();
    }

    private static boolean isClientSecurityHeader(String name) {
        return HttpHeaders.ORIGIN.equalsIgnoreCase(name)
                || HttpHeaders.AUTHORIZATION.equalsIgnoreCase(name)
                || startsWithIgnoreCase(name, "X-User-")
                || startsWithIgnoreCase(name, "X-Project-")
                || startsWithIgnoreCase(name, "X-Board-")
                || startsWithIgnoreCase(name, "X-Gateway-")
                || startsWithIgnoreCase(name, "X-Service-");
    }

    private static boolean startsWithIgnoreCase(String value, String prefix) {
        return value.regionMatches(true, 0, prefix, 0, prefix.length());
    }

    private static Mono<Void> respond(ServerWebExchange exchange, HttpStatus status) {
        exchange.getResponse().setStatusCode(status);
        return exchange.getResponse().setComplete();
    }

    private record AuthorizationResult(UUID accountId, HttpStatus rejectionStatus) {

        private static AuthorizationResult allowed(UUID accountId) {
            return new AuthorizationResult(accountId, null);
        }

        private static AuthorizationResult rejected(HttpStatus status) {
            return new AuthorizationResult(null, status);
        }
    }
}
