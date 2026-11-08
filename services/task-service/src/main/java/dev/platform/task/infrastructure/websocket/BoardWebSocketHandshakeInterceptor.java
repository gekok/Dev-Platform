package dev.platform.task.infrastructure.websocket;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import dev.platform.task.application.exception.AuthorizationSnapshotUnavailableException;
import dev.platform.task.application.exception.ProjectAccessDeniedException;
import dev.platform.task.application.port.out.BoardProjectLookupPort;
import dev.platform.task.application.service.ProjectAuthorizationGuard;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

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
public class BoardWebSocketHandshakeInterceptor implements HandshakeInterceptor {

    static final String BOARD_ID = "task.boardId";
    static final String PROJECT_ID = "task.projectId";
    static final String USER_ID = "task.userId";

    private final BoardProjectLookupPort boardProjects;
    private final ProjectAuthorizationGuard authorization;

    @Override
    public boolean beforeHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler handler,
            Map<String, Object> attributes) {
        Long boardId;
        try {
            boardId = boardIdFromPath(request.getURI().getPath());
        } catch (IllegalArgumentException exception) {
            response.setStatusCode(HttpStatus.BAD_REQUEST);
            return false;
        }

        UUID userId;
        String identityHeader = request.getHeaders().getFirst("X-User-Id");
        try {
            if (identityHeader == null || identityHeader.isBlank()) {
                throw new IllegalArgumentException("Missing trusted identity");
            }
            userId = UUID.fromString(identityHeader);
        } catch (IllegalArgumentException exception) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }

        try {
            Optional<Long> projectLookup = boardProjects.findProjectId(boardId);
            if (projectLookup.isEmpty()) {
                response.setStatusCode(HttpStatus.NOT_FOUND);
                return false;
            }
            Long projectId = projectLookup.get();
            authorization.requireProjectMember(projectId, userId);
            attributes.put(BOARD_ID, boardId);
            attributes.put(PROJECT_ID, projectId);
            attributes.put(USER_ID, userId.toString());
            return true;
        } catch (ProjectAccessDeniedException exception) {
            response.setStatusCode(HttpStatus.FORBIDDEN);
            return false;
        } catch (AuthorizationSnapshotUnavailableException exception) {
            log.warn("WebSocket authorization snapshot unavailable for board {}", boardId);
            response.setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);
            return false;
        } catch (RuntimeException exception) {
            log.warn("WebSocket Board lookup failed for board {}", boardId, exception);
            response.setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);
            return false;
        }
    }

    @Override
    public void afterHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler handler,
            Exception exception) {
        // No handshake state remains outside the authenticated session attributes.
    }

    private static Long boardIdFromPath(String path) {
        if (path == null) {
            throw new IllegalArgumentException("Board path is required");
        }
        String[] parts = path.split("/");
        if (parts.length < 4 || !"ws".equals(parts[1]) || !"boards".equals(parts[2])) {
            throw new IllegalArgumentException("Invalid Board WebSocket path");
        }
        long boardId = Long.parseLong(parts[3]);
        if (boardId < 1 || parts.length != 4) {
            throw new IllegalArgumentException("Invalid Board ID");
        }
        return boardId;
    }
}
