package dev.platform.task.infrastructure.websocket;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.platform.task.application.exception.AuthorizationSnapshotUnavailableException;
import dev.platform.task.application.port.out.BoardProjectLookupPort;
import dev.platform.task.application.service.ProjectAuthorizationGuard;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = {
                "dev-platform.security.trusted-gateway-identity.enabled",
                "dev-platform.realtime.board-lookup.ready",
                "dev-platform.realtime.gateway-proxy.ready",
                "dev-platform.realtime.board-websocket.enabled"
        },
        havingValue = "true")
public class BoardWebSocketHandler extends TextWebSocketHandler {

    private final ConcurrentMap<Long, ConcurrentMap<String, WebSocketSession>> sessionsByBoard =
            new ConcurrentHashMap<>();

    private final BoardProjectLookupPort boardProjects;
    private final ProjectAuthorizationGuard authorization;
    private final ObjectMapper objectMapper;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws IOException {
        if (!isAuthorized(session)) {
            close(session);
            return;
        }
        Long boardId = attribute(session, BoardWebSocketHandshakeInterceptor.BOARD_ID, Long.class);
        WebSocketSession safeSession = new ConcurrentWebSocketSessionDecorator(session, 5_000, 64 * 1024);
        sessionsByBoard.compute(boardId, (ignored, sessions) -> {
            if (!safeSession.isOpen()) {
                return sessions;
            }
            ConcurrentMap<String, WebSocketSession> boardSessions =
                    sessions == null ? new ConcurrentHashMap<>() : sessions;
            boardSessions.put(safeSession.getId(), safeSession);
            return boardSessions;
        });
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Long boardId = attribute(session, BoardWebSocketHandshakeInterceptor.BOARD_ID, Long.class);
        remove(boardId, session.getId());
    }

    @Scheduled(fixedDelayString = "${dev-platform.realtime.board-websocket.authorization-recheck-ms:30000}")
    public void reauthorizeConnectedSessions() {
        sessionsByBoard.forEach((boardId, sessions) -> sessions.forEach((sessionId, session) -> {
            if (!session.isOpen() || !isAuthorized(session)) {
                close(session);
                remove(boardId, sessionId);
            }
        }));
    }

    public void broadcast(Long boardId, Object payload) {
        ConcurrentMap<String, WebSocketSession> sessions = sessionsByBoard.get(boardId);
        if (sessions == null || sessions.isEmpty()) {
            return;
        }

        final TextMessage message;
        try {
            message = new TextMessage(objectMapper.writeValueAsString(payload));
        } catch (JsonProcessingException exception) {
            log.warn("Could not serialize Board WebSocket event for board {}", boardId, exception);
            return;
        }

        sessions.forEach((sessionId, session) -> {
            if (!session.isOpen() || !isAuthorized(session)) {
                close(session);
                remove(boardId, sessionId);
                return;
            }
            try {
                session.sendMessage(message);
            } catch (IOException | RuntimeException exception) {
                log.debug("Dropping failed best-effort WebSocket session {}", sessionId, exception);
                close(session);
                remove(boardId, sessionId);
            }
        });
    }

    private boolean isAuthorized(WebSocketSession session) {
        try {
            Long boardId = attribute(session, BoardWebSocketHandshakeInterceptor.BOARD_ID, Long.class);
            Long expectedProjectId = attribute(session, BoardWebSocketHandshakeInterceptor.PROJECT_ID, Long.class);
            String rawUserId = attribute(session, BoardWebSocketHandshakeInterceptor.USER_ID, String.class);
            UUID userId = UUID.fromString(rawUserId);
            Optional<Long> projectLookup = boardProjects.findProjectId(boardId);
            if (projectLookup.isEmpty()) {
                return false;
            }
            Long currentProjectId = projectLookup.get();
            if (!expectedProjectId.equals(currentProjectId)) {
                return false;
            }
            authorization.requireProjectMember(currentProjectId, userId);
            return true;
        } catch (AuthorizationSnapshotUnavailableException exception) {
            return false;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private static <T> T attribute(WebSocketSession session, String key, Class<T> type) {
        Object value = session.getAttributes().get(key);
        if (!type.isInstance(value)) {
            throw new IllegalStateException("Missing WebSocket authorization attribute " + key);
        }
        return type.cast(value);
    }

    private void remove(Long boardId, String sessionId) {
        if (boardId == null) {
            return;
        }
        sessionsByBoard.computeIfPresent(boardId, (ignored, sessions) -> {
            sessions.remove(sessionId);
            return sessions.isEmpty() ? null : sessions;
        });
    }

    private static void close(WebSocketSession session) {
        if (session != null && session.isOpen()) {
            try {
                session.close(CloseStatus.POLICY_VIOLATION);
            } catch (IOException exception) {
                logCloseFailure(session, exception);
            }
        }
    }

    private static void logCloseFailure(WebSocketSession session, IOException exception) {
        // Close failures are best-effort; the session is removed from local fanout regardless.
        org.slf4j.LoggerFactory.getLogger(BoardWebSocketHandler.class)
                .debug("Could not close WebSocket session {}", session.getId(), exception);
    }
}
