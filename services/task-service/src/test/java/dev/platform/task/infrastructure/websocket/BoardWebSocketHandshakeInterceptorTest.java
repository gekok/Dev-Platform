package dev.platform.task.infrastructure.websocket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import dev.platform.task.application.exception.AuthorizationSnapshotUnavailableException;
import dev.platform.task.application.exception.ProjectAccessDeniedException;
import dev.platform.task.application.port.out.BoardProjectLookupPort;
import dev.platform.task.application.service.BoardStateUnavailableException;
import dev.platform.task.application.service.ProjectAuthorizationGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;

class BoardWebSocketHandshakeInterceptorTest {

    private static final long BOARD_ID = 42L;
    private static final long PROJECT_ID = 100L;
    private static final UUID USER_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");

    private final BoardProjectLookupPort boardProjects = mock(BoardProjectLookupPort.class);
    private final ProjectAuthorizationGuard authorization = mock(ProjectAuthorizationGuard.class);
    private final ServerHttpRequest request = mock(ServerHttpRequest.class);
    private final ServerHttpResponse response = mock(ServerHttpResponse.class);
    private final WebSocketHandler handler = mock(WebSocketHandler.class);
    private final Map<String, Object> attributes = new HashMap<>();
    private final BoardWebSocketHandshakeInterceptor interceptor =
            new BoardWebSocketHandshakeInterceptor(boardProjects, authorization);

    @BeforeEach
    void setUp() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-User-Id", USER_ID.toString());
        when(request.getURI()).thenReturn(URI.create("http://localhost/ws/boards/" + BOARD_ID));
        when(request.getHeaders()).thenReturn(headers);
        when(boardProjects.findProjectId(BOARD_ID)).thenReturn(Optional.of(PROJECT_ID));
    }

    @Test
    void acceptsActiveProjectMemberAndStoresAuthorizationContext() {
        boolean accepted = interceptor.beforeHandshake(request, response, handler, attributes);

        assertTrue(accepted);
        assertEquals(BOARD_ID, attributes.get(BoardWebSocketHandshakeInterceptor.BOARD_ID));
        assertEquals(PROJECT_ID, attributes.get(BoardWebSocketHandshakeInterceptor.PROJECT_ID));
        assertEquals(USER_ID.toString(), attributes.get(BoardWebSocketHandshakeInterceptor.USER_ID));
        verify(authorization).requireProjectMember(PROJECT_ID, USER_ID);
    }

    @Test
    void rejectsRevokedMembership() {
        when(authorization.requireProjectMember(PROJECT_ID, USER_ID))
                .thenThrow(new ProjectAccessDeniedException("Membership is revoked"));

        boolean accepted = interceptor.beforeHandshake(request, response, handler, attributes);

        assertFalse(accepted);
        verify(response).setStatusCode(HttpStatus.FORBIDDEN);
    }

    @Test
    void returnsNotFoundForMissingBoardWithoutCheckingMembership() {
        when(boardProjects.findProjectId(BOARD_ID)).thenReturn(Optional.empty());

        boolean accepted = interceptor.beforeHandshake(request, response, handler, attributes);

        assertFalse(accepted);
        verify(response).setStatusCode(HttpStatus.NOT_FOUND);
        verify(authorization, never()).requireProjectMember(any(), any());
    }

    @Test
    void failsClosedWhenBoardLookupIsUnavailable() {
        when(boardProjects.findProjectId(BOARD_ID))
                .thenThrow(new BoardStateUnavailableException("Board Service unavailable", null));

        boolean accepted = interceptor.beforeHandshake(request, response, handler, attributes);

        assertFalse(accepted);
        verify(response).setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void failsClosedWhenAuthorizationSnapshotIsUnavailable() {
        when(authorization.requireProjectMember(PROJECT_ID, USER_ID))
                .thenThrow(new AuthorizationSnapshotUnavailableException("Redis unavailable"));

        boolean accepted = interceptor.beforeHandshake(request, response, handler, attributes);

        assertFalse(accepted);
        verify(response).setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void rejectsMissingIdentityBeforeLookup() {
        when(request.getHeaders()).thenReturn(new HttpHeaders());

        boolean accepted = interceptor.beforeHandshake(request, response, handler, attributes);

        assertFalse(accepted);
        verify(response).setStatusCode(HttpStatus.UNAUTHORIZED);
        verify(boardProjects, never()).findProjectId(BOARD_ID);
    }
}
