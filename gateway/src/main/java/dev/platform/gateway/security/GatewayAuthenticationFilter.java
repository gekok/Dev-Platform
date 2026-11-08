package dev.platform.gateway.security;

import java.security.interfaces.RSAPublicKey;
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
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;

@Component
public class GatewayAuthenticationFilter implements WebFilter, Ordered {

    private static final String USER_ID_HEADER = "X-User-Id";
    private static final String LOGIN_PATH = "/api/v1/auth/login";
    private static final String PUBLIC_KEY_PATH = "/api/v1/auth/public-key";
    private static final String HEALTH_PATH = "/actuator/health";

    private final UserServicePublicKeyProvider publicKeyProvider;
    private final RedisAccountSnapshotReader snapshotReader;
    private final String issuer;

    public GatewayAuthenticationFilter(
            UserServicePublicKeyProvider publicKeyProvider,
            RedisAccountSnapshotReader snapshotReader,
            @Value("${app.jwt.issuer:dev-platform-user-service}") String issuer) {
        this.publicKeyProvider = publicKeyProvider;
        this.snapshotReader = snapshotReader;
        this.issuer = issuer;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
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
