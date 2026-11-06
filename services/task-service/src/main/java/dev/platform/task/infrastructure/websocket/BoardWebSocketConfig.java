package dev.platform.task.infrastructure.websocket;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = {
                "dev-platform.security.trusted-gateway-identity.enabled",
                "dev-platform.realtime.board-lookup.ready",
                "dev-platform.realtime.gateway-proxy.ready",
                "dev-platform.realtime.board-websocket.enabled"
        },
        havingValue = "true")
public class BoardWebSocketConfig implements WebSocketConfigurer {

    private final BoardWebSocketHandler handler;
    private final BoardWebSocketHandshakeInterceptor handshakeInterceptor;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/ws/boards/{boardId}")
                .addInterceptors(handshakeInterceptor);
    }
}
