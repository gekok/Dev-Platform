package dev.platform.gateway;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(GatewayApplicationTest.RedisTestConfiguration.class)
class GatewayApplicationTest {

    private static final String ISSUER = "dev-platform-user-service";
    private static final String ACCOUNT_KEY_PREFIX = "auth:account:";
    private static final UUID ACCOUNT_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final KeyPair JWT_KEY_PAIR = generateRsaKeyPair();
    private static final KeyPair WRONG_JWT_KEY_PAIR = generateRsaKeyPair();
    private static final AtomicReference<Integer> USER_PUBLIC_KEY_STATUS = new AtomicReference<>(200);
    private static final AtomicReference<String> USER_PUBLIC_KEY_BODY = new AtomicReference<>(publicKeyResponse());
    private static final AtomicReference<Function<String, Mono<String>>> SNAPSHOT_LOOKUP =
            new AtomicReference<>(key -> Mono.empty());
    private static final ReactiveStringRedisTemplate REDIS = mock(ReactiveStringRedisTemplate.class);
    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();
    @SuppressWarnings("unchecked")
    private static final ReactiveValueOperations<String, String> REDIS_VALUES = mock(ReactiveValueOperations.class);

    private static final DisposableServer USER = upstream("user");
    private static final DisposableServer BOARD = upstream("board");
    private static final DisposableServer TASK = upstream("task");
    private static final DisposableServer NOTIFICATION = upstream("notification");

    static {
        when(REDIS.opsForValue()).thenReturn(REDIS_VALUES);
    }

    @Value("${local.server.port}")
    int port;

    @TestConfiguration
    static class RedisTestConfiguration {
        @Bean
        @Primary
        ReactiveStringRedisTemplate testReactiveStringRedisTemplate() {
            return REDIS;
        }
    }

    private static KeyPair generateRsaKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (Exception exception) {
            throw new IllegalStateException("Could not create test RSA key pair", exception);
        }
    }

    private static String publicKeyResponse() {
        String encoded = Base64.getMimeEncoder(64, new byte[]{'\n'})
                .encodeToString(JWT_KEY_PAIR.getPublic().getEncoded());
        String pem = "-----BEGIN PUBLIC KEY-----\n" + encoded + "\n-----END PUBLIC KEY-----";
        return "{\"algorithm\":\"RSA256\",\"publicKey\":\"" + pem.replace("\n", "\\n") + "\"}";
    }

    private static DisposableServer upstream(String name) {
        return HttpServer.create().host("127.0.0.1").port(0)
                .handle((request, response) -> {
                    String path = request.uri();
                    if ("user".equals(name) && "/api/v1/auth/public-key".equals(path)) {
                        return response.status(USER_PUBLIC_KEY_STATUS.get())
                                .header("Content-Type", "application/json")
                                .sendString(Mono.just(USER_PUBLIC_KEY_BODY.get()));
                    }

                    String body = name + ":" + path;
                    if ("user".equals(name) && "/api/v1/users/me".equals(path)) {
                        body += ":" + request.requestHeaders().get("X-User-Id")
                                + ":" + request.requestHeaders().get("Authorization");
                    } else if ("user".equals(name) && "/api/v1/auth/login".equals(path)) {
                        body += ":" + request.requestHeaders().get("X-User-Id");
                    }
                    return response.sendString(Mono.just(body));
                })
                .bindNow();
    }

    @DynamicPropertySource
    static void upstreams(DynamicPropertyRegistry registry) {
        registry.add("gateway.upstream.user", () -> "http://127.0.0.1:" + USER.port());
        registry.add("gateway.upstream.board", () -> "http://127.0.0.1:" + BOARD.port());
        registry.add("gateway.upstream.task", () -> "http://127.0.0.1:" + TASK.port());
        registry.add("gateway.upstream.notification", () -> "http://127.0.0.1:" + NOTIFICATION.port());
        registry.add("app.jwt.issuer", () -> ISSUER);
        registry.add("spring.data.redis.host", () -> "localhost");
        registry.add("spring.data.redis.port", () -> 6379);
        registry.add("management.health.redis.enabled", () -> false);
    }

    @BeforeEach
    void setUpTestSnapshots() {
        USER_PUBLIC_KEY_STATUS.set(200);
        USER_PUBLIC_KEY_BODY.set(publicKeyResponse());
        SNAPSHOT_LOOKUP.set(key -> {
            UUID accountId = UUID.fromString(key.substring(ACCOUNT_KEY_PREFIX.length()));
            return Mono.just(snapshot(accountId, "ACTIVE", 1));
        });
        reset(REDIS_VALUES);
        when(REDIS_VALUES.get(anyString())).thenAnswer(invocation ->
                SNAPSHOT_LOOKUP.get().apply(invocation.getArgument(0)));
    }

    @AfterAll
    static void stopUpstreams() {
        USER.disposeNow();
        BOARD.disposeNow();
        TASK.disposeNow();
        NOTIFICATION.disposeNow();
        HTTP_CLIENT.close();
    }

    @Test
    void onlyConfiguredPublicRoutesBypassAuthentication() throws Exception {
        HttpResponse<String> login = postWithIdentityHeader("/api/v1/auth/login", UUID.randomUUID().toString());
        assertEquals(200, login.statusCode());
        assertEquals("user:/api/v1/auth/login:null", login.body());
        assertEquals(200, getWithoutToken("/api/v1/auth/public-key").statusCode());
        assertEquals(200, getWithoutToken("/actuator/health").statusCode());
        assertEquals(401, getWithoutToken("/api/v1/auth/login").statusCode());
        assertEquals(401, request("HEAD", "/actuator/health", null, null).statusCode());
    }

    @Test
    void forwardsProjectScopedCardsBeforeProjectRoutes() throws Exception {
        assertRoute("/api/v1/projects/1/boards/2/cards?columnId=3", "task");
        assertRoute("/api/v1/projects/1/cards/4/comments", "task");
        assertRoute("/api/v1/projects/invitations", "board");
        assertRoute("/api/v1/projects/1/boards", "board");
        assertRoute("/api/v1/users/me", "user", validToken(ACCOUNT_ID));
        assertRoute("/api/v1/notifications/me", "notification");
    }

    @Test
    void doesNotExposeInternalRoutes() throws Exception {
        assertEquals(404, getWithToken("/internal/v1/notifications", validToken(ACCOUNT_ID)).statusCode());
    }

    @Test
    void rejectsRequestsWithoutBearerToken() throws Exception {
        assertEquals(401, getWithoutToken("/api/v1/users/me").statusCode());
    }

    @Test
    void rejectsMalformedToken() throws Exception {
        assertEquals(401, getWithToken("/api/v1/users/me", "not-a-jwt").statusCode());
    }

    @Test
    void rejectsExpiredToken() throws Exception {
        String token = token(ACCOUNT_ID.toString(), ISSUER, Date.from(Instant.now().minusSeconds(3600)),
                Date.from(Instant.now().minusSeconds(60)), JWT_KEY_PAIR);
        assertEquals(401, getWithToken("/api/v1/users/me", token).statusCode());
    }

    @Test
    void rejectsTokenFromWrongIssuer() throws Exception {
        String token = token(ACCOUNT_ID.toString(), "untrusted-issuer", new Date(),
                Date.from(Instant.now().plusSeconds(3600)), JWT_KEY_PAIR);
        assertEquals(401, getWithToken("/api/v1/users/me", token).statusCode());
    }

    @Test
    void rejectsTokenWithoutIssuedAt() throws Exception {
        String token = token(ACCOUNT_ID.toString(), ISSUER, null,
                Date.from(Instant.now().plusSeconds(3600)), JWT_KEY_PAIR);
        assertEquals(401, getWithToken("/api/v1/users/me", token).statusCode());
    }

    @Test
    void rejectsTokenWithoutExpiration() throws Exception {
        String token = token(ACCOUNT_ID.toString(), ISSUER, new Date(), null, JWT_KEY_PAIR);
        assertEquals(401, getWithToken("/api/v1/users/me", token).statusCode());
    }

    @Test
    void rejectsTokenWithInvalidSignature() throws Exception {
        String token = token(ACCOUNT_ID.toString(), ISSUER, new Date(),
                Date.from(Instant.now().plusSeconds(3600)), WRONG_JWT_KEY_PAIR);
        assertEquals(401, getWithToken("/api/v1/users/me", token).statusCode());
    }

    @Test
    void rejectsNonUuidSubject() throws Exception {
        String token = token("not-a-uuid", ISSUER, new Date(),
                Date.from(Instant.now().plusSeconds(3600)), JWT_KEY_PAIR);
        assertEquals(401, getWithToken("/api/v1/users/me", token).statusCode());
    }

    @Test
    void activeAccountSnapshotAllowsRequestAndOverridesSpoofedIdentityHeader() throws Exception {
        UUID accountId = UUID.randomUUID();
        String token = validToken(accountId);
        String spoofedId = UUID.randomUUID().toString();

        HttpResponse<String> response = getWithIdentityHeader("/api/v1/users/me", token, spoofedId);

        assertEquals(200, response.statusCode());
        assertTrue(response.body().startsWith("user:/api/v1/users/me:" + accountId + ":Bearer "));
        assertTrue(response.body().endsWith(token));
    }

    @Test
    void inactiveAccountSnapshotReturnsForbidden() throws Exception {
        SNAPSHOT_LOOKUP.set(key -> Mono.just(snapshot(
                UUID.fromString(key.substring(ACCOUNT_KEY_PREFIX.length())), "SUSPENDED", 2)));

        assertEquals(403, getWithToken("/api/v1/users/me", validToken(ACCOUNT_ID)).statusCode());
    }

    @Test
    void missingAccountSnapshotFailsClosed() throws Exception {
        SNAPSHOT_LOOKUP.set(key -> Mono.empty());
        assertEquals(503, getWithToken("/api/v1/users/me", validToken(ACCOUNT_ID)).statusCode());
    }

    @Test
    void malformedAccountSnapshotFailsClosed() throws Exception {
        SNAPSHOT_LOOKUP.set(key -> Mono.just("{"));
        assertEquals(503, getWithToken("/api/v1/users/me", validToken(ACCOUNT_ID)).statusCode());
    }

    @Test
    void mismatchedAccountSnapshotFailsClosed() throws Exception {
        SNAPSHOT_LOOKUP.set(key -> Mono.just(snapshot(UUID.randomUUID(), "ACTIVE", 1)));
        assertEquals(503, getWithToken("/api/v1/users/me", validToken(ACCOUNT_ID)).statusCode());
    }

    @Test
    void invalidAccountSnapshotVersionFailsClosed() throws Exception {
        SNAPSHOT_LOOKUP.set(key -> Mono.just(snapshot(ACCOUNT_ID, "ACTIVE", 0)));
        assertEquals(503, getWithToken("/api/v1/users/me", validToken(ACCOUNT_ID)).statusCode());
    }

    @Test
    void unavailableAccountSnapshotFailsClosed() throws Exception {
        SNAPSHOT_LOOKUP.set(key -> Mono.error(new IllegalStateException("Redis unavailable")));
        assertEquals(503, getWithToken("/api/v1/users/me", validToken(ACCOUNT_ID)).statusCode());
    }

    @Test
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.BEFORE_METHOD)
    void publicKeyProviderFailureReturnsServiceUnavailable() throws Exception {
        USER_PUBLIC_KEY_STATUS.set(503);
        assertEquals(503, getWithToken("/api/v1/users/me", validToken(ACCOUNT_ID)).statusCode());
    }

    @Test
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.BEFORE_METHOD)
    void publicKeyProviderDoesNotCacheFailedFetch() throws Exception {
        USER_PUBLIC_KEY_STATUS.set(503);
        assertEquals(503, getWithToken("/api/v1/users/me", validToken(ACCOUNT_ID)).statusCode());

        USER_PUBLIC_KEY_STATUS.set(200);
        assertEquals(200, getWithToken("/api/v1/users/me", validToken(ACCOUNT_ID)).statusCode());
    }

    @Test
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.BEFORE_METHOD)
    void publicKeyProviderCachesSuccessfulKey() throws Exception {
        assertEquals(200, getWithToken("/api/v1/users/me", validToken(ACCOUNT_ID)).statusCode());

        USER_PUBLIC_KEY_STATUS.set(503);
        assertEquals(200, getWithToken("/api/v1/users/me", validToken(ACCOUNT_ID)).statusCode());
    }

    @Test
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.BEFORE_METHOD)
    void publicKeyProviderRejectsNonRsa256Response() throws Exception {
        USER_PUBLIC_KEY_BODY.set("{\"algorithm\":\"HS256\",\"publicKey\":\"invalid\"}");
        assertEquals(503, getWithToken("/api/v1/users/me", validToken(ACCOUNT_ID)).statusCode());
    }

    private static String snapshot(UUID accountId, String status, long authVersion) {
        return "{\"accountId\":\"" + accountId + "\",\"status\":\"" + status
                + "\",\"systemRoles\":[\"USER\"],\"authVersion\":" + authVersion + "}";
    }

    private static String validToken(UUID accountId) {
        return token(accountId.toString(), ISSUER, new Date(),
                Date.from(Instant.now().plusSeconds(3600)), JWT_KEY_PAIR);
    }

    private static String token(String subject, String issuer, Date issuedAt, Date expiresAt, KeyPair signingKey) {
        var builder = JWT.create().withIssuer(issuer).withSubject(subject);
        if (issuedAt != null) {
            builder.withIssuedAt(issuedAt);
        }
        if (expiresAt != null) {
            builder.withExpiresAt(expiresAt);
        }
        return builder.sign(Algorithm.RSA256(
                (RSAPublicKey) signingKey.getPublic(), (RSAPrivateKey) signingKey.getPrivate()));
    }

    private void assertRoute(String path, String expected) throws Exception {
        HttpResponse<String> response = get(path);
        assertEquals(200, response.statusCode(), path);
        assertEquals(expected + ":" + path, response.body());
    }

    private void assertRoute(String path, String expected, String token) throws Exception {
        HttpResponse<String> response = getWithToken(path, token);
        assertEquals(200, response.statusCode(), path);
        assertTrue(response.body().startsWith(expected + ":" + path), path);
    }

    private HttpResponse<String> get(String path) throws Exception {
        return getWithToken(path, validToken(ACCOUNT_ID));
    }

    private HttpResponse<String> getWithoutToken(String path) throws Exception {
        return request("GET", path, null, null);
    }

    private HttpResponse<String> getWithToken(String path, String token) throws Exception {
        return request("GET", path, token, null);
    }

    private HttpResponse<String> getWithIdentityHeader(String path, String token, String identityHeader)
            throws Exception {
        return request("GET", path, token, identityHeader);
    }

    private HttpResponse<String> postWithIdentityHeader(String path, String identityHeader) throws Exception {
        return request("POST", path, null, identityHeader);
    }

    private HttpResponse<String> request(String method, String path, String token, String identityHeader)
            throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + path));
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        if (identityHeader != null) {
            builder.header("X-User-Id", identityHeader);
        }
        switch (method) {
            case "POST" -> builder.POST(HttpRequest.BodyPublishers.noBody());
            case "HEAD" -> builder.method("HEAD", HttpRequest.BodyPublishers.noBody());
            default -> builder.GET();
        }
        return HTTP_CLIENT.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }
}
