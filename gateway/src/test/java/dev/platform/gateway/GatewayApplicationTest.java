package dev.platform.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.junit.jupiter.api.AfterAll;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewayApplicationTest {

    private static final DisposableServer USER = upstream("user");
    private static final DisposableServer BOARD = upstream("board");
    private static final DisposableServer TASK = upstream("task");
    private static final DisposableServer NOTIFICATION = upstream("notification");

    @Value("${local.server.port}")
    int port;

    private static DisposableServer upstream(String name) {
        return HttpServer.create().host("127.0.0.1").port(0)
                .handle((request, response) -> response.sendString(
                        reactor.core.publisher.Mono.just(name + ":" + request.uri())))
                .bindNow();
    }

    @DynamicPropertySource
    static void upstreams(DynamicPropertyRegistry registry) {
        registry.add("gateway.upstream.user", () -> "http://127.0.0.1:" + USER.port());
        registry.add("gateway.upstream.board", () -> "http://127.0.0.1:" + BOARD.port());
        registry.add("gateway.upstream.task", () -> "http://127.0.0.1:" + TASK.port());
        registry.add("gateway.upstream.notification", () -> "http://127.0.0.1:" + NOTIFICATION.port());
    }

    @AfterAll
    static void stopUpstreams() {
        USER.disposeNow();
        BOARD.disposeNow();
        TASK.disposeNow();
        NOTIFICATION.disposeNow();
    }

    @Test
    void startsGatewayWithConfiguredRoutes() {
    }

    @Test
    void forwardsProjectScopedCardsBeforeProjectRoutes() throws Exception {
        assertRoute("/api/v1/projects/1/boards/2/cards?columnId=3", "task");
        assertRoute("/api/v1/projects/1/cards/4/comments", "task");
        assertRoute("/api/v1/projects/invitations", "board");
        assertRoute("/api/v1/projects/1/boards", "board");
        assertRoute("/api/v1/auth/login", "user");
        assertRoute("/api/v1/users/me", "user");
        assertRoute("/api/v1/notifications/me", "notification");
    }

    @Test
    void doesNotExposeInternalRoutes() throws Exception {
        assertEquals(404, get("/internal/v1/notifications").statusCode());
    }

    private void assertRoute(String path, String expected) throws Exception {
        HttpResponse<String> response = get(path);
        assertEquals(200, response.statusCode(), path);
        assertEquals(expected + ":" + path, response.body());
    }

    private HttpResponse<String> get(String path) throws Exception {
        return HttpClient.newHttpClient().send(HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + path)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }
}
