package dev.platform.task.infrastructure.websocket;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.platform.task.application.exception.ProjectAccessDeniedException;
import dev.platform.task.application.port.out.BoardProjectLookupPort;
import dev.platform.task.application.service.ProjectAuthorizationGuard;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

class BoardWebSocketHandlerTest {

    @Test
    void broadcastClosesSessionAfterMembershipRevocation() throws Exception {
        long boardId = 7L;
        long projectId = 9L;
        UUID userId = UUID.randomUUID();
        BoardProjectLookupPort boardProjects = mock(BoardProjectLookupPort.class);
        when(boardProjects.findProjectId(boardId)).thenReturn(Optional.of(projectId));
        ProjectAuthorizationGuard authorization = mock(ProjectAuthorizationGuard.class);
        when(authorization.requireProjectMember(projectId, userId)).thenReturn(null);
        BoardWebSocketHandler handler = new BoardWebSocketHandler(
                boardProjects, authorization, new ObjectMapper());
        WebSocketSession session = session("revoked-session", boardId, projectId, userId);
        handler.afterConnectionEstablished(session);

        when(authorization.requireProjectMember(projectId, userId))
                .thenThrow(new ProjectAccessDeniedException("Membership is revoked"));
        handler.broadcast(boardId, Map.of("event", "card.created"));

        verify(session).close(CloseStatus.POLICY_VIOLATION);
        verify(session, never()).sendMessage(any());
    }

    @Test
    void periodicAuthorizationCheckClosesSessionAfterBoardIsRemoved() throws Exception {
        long boardId = 7L;
        long projectId = 9L;
        UUID userId = UUID.randomUUID();
        BoardProjectLookupPort boardProjects = mock(BoardProjectLookupPort.class);
        when(boardProjects.findProjectId(boardId)).thenReturn(Optional.of(projectId));
        ProjectAuthorizationGuard authorization = mock(ProjectAuthorizationGuard.class);
        when(authorization.requireProjectMember(projectId, userId)).thenReturn(null);
        BoardWebSocketHandler handler = new BoardWebSocketHandler(
                boardProjects, authorization, new ObjectMapper());
        WebSocketSession session = session("removed-board-session", boardId, projectId, userId);
        handler.afterConnectionEstablished(session);

        when(boardProjects.findProjectId(boardId)).thenReturn(Optional.empty());
        handler.reauthorizeConnectedSessions();

        verify(session).close(CloseStatus.POLICY_VIOLATION);
    }

    @Test
    void closingLastSessionCannotDetachConcurrentNewRegistration() throws Exception {
        long boardId = 7L;
        long projectId = 9L;
        UUID userId = UUID.randomUUID();
        BoardProjectLookupPort boardProjects = mock(BoardProjectLookupPort.class);
        when(boardProjects.findProjectId(boardId)).thenReturn(Optional.of(projectId));
        ProjectAuthorizationGuard authorization = mock(ProjectAuthorizationGuard.class);
        when(authorization.requireProjectMember(projectId, userId)).thenReturn(null);
        BoardWebSocketHandler handler = new BoardWebSocketHandler(
                boardProjects, authorization, new ObjectMapper());

        WebSocketSession oldSession = session("old-session", boardId, projectId, userId);
        handler.afterConnectionEstablished(oldSession);

        CountDownLatch registrationIdRequested = new CountDownLatch(1);
        CountDownLatch allowRegistration = new CountDownLatch(1);
        WebSocketSession newSession = session("new-session", boardId, projectId, userId);
        when(newSession.getId()).thenAnswer(invocation -> {
            registrationIdRequested.countDown();
            if (!allowRegistration.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting to finish session registration");
            }
            return "new-session";
        });

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> registration = executor.submit(() -> {
                try {
                    handler.afterConnectionEstablished(newSession);
                } catch (java.io.IOException exception) {
                    throw new RuntimeException(exception);
                }
            });
            assertTrue(registrationIdRequested.await(5, TimeUnit.SECONDS));

            CountDownLatch removalStarted = new CountDownLatch(1);
            CountDownLatch oldSessionClosed = new CountDownLatch(1);
            Future<?> removal = executor.submit(() -> {
                removalStarted.countDown();
                handler.afterConnectionClosed(oldSession, CloseStatus.NORMAL);
                oldSessionClosed.countDown();
            });
            assertTrue(removalStarted.await(5, TimeUnit.SECONDS));
            assertFalse(oldSessionClosed.await(250, TimeUnit.MILLISECONDS),
                    "removal of the final old session must wait for the atomic registration");

            allowRegistration.countDown();
            registration.get(5, TimeUnit.SECONDS);
            removal.get(5, TimeUnit.SECONDS);

            handler.broadcast(boardId, Map.of("event", "card.created"));
            verify(newSession, timeout(1_000)).sendMessage(any(TextMessage.class));
        } finally {
            allowRegistration.countDown();
            executor.shutdownNow();
        }
    }

    private static WebSocketSession session(String id, long boardId, long projectId, UUID userId) {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn(id);
        when(session.isOpen()).thenReturn(true);
        when(session.getAttributes()).thenReturn(new HashMap<>(Map.of(
                BoardWebSocketHandshakeInterceptor.BOARD_ID, boardId,
                BoardWebSocketHandshakeInterceptor.PROJECT_ID, projectId,
                BoardWebSocketHandshakeInterceptor.USER_ID, userId.toString())));
        return session;
    }
}
