package dev.platform.task.infrastructure.websocket;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

// Cấu hình kênh WS theo bảng, kỳ này chỉ giữ chỗ biên dịch.
// TODO kỳ 1: đăng ký handler cho /ws/boards/* bắn card.created/moved/comment.added.
@Configuration
@EnableWebSocket
public class BoardWebSocketConfig implements WebSocketConfigurer {

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // TODO kỳ 1: registry.addHandler(boardHandler(), "/ws/boards/*").setAllowedOrigins("*");
    }
}
