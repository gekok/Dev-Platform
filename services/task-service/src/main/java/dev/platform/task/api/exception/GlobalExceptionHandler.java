package dev.platform.task.api.exception;

import java.time.OffsetDateTime;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import dev.platform.task.application.service.AuthorizationSnapshotUnavailableException;
import dev.platform.task.application.service.ProjectAccessDeniedException;
import jakarta.servlet.http.HttpServletRequest;

// Bắt lỗi validate Aggregate, trả 400 theo kanban-api.md mục 6.
@RestControllerAdvice
public class GlobalExceptionHandler {

    // Lỗi validate đầu vào (title trống, position âm...).
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleBadRequest(
            IllegalArgumentException ex, HttpServletRequest request) {
        ErrorResponse body = new ErrorResponse(
                "BAD_REQUEST",
                ex.getMessage(),
                OffsetDateTime.now().toString(),
                request.getRequestURI());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(ProjectAccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleProjectAccessDenied(
            ProjectAccessDeniedException ex, HttpServletRequest request) {
        ErrorResponse body = new ErrorResponse(
                "PROJECT_ACCESS_DENIED", ex.getMessage(), OffsetDateTime.now().toString(), request.getRequestURI());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body);
    }

    @ExceptionHandler(AuthorizationSnapshotUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleSnapshotUnavailable(
            AuthorizationSnapshotUnavailableException ex, HttpServletRequest request) {
        ErrorResponse body = new ErrorResponse(
                "AUTHORIZATION_SNAPSHOT_UNAVAILABLE", ex.getMessage(), OffsetDateTime.now().toString(),
                request.getRequestURI());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(body);
    }

    // Body lỗi chuẩn mục 6 kanban-api.md.
    public record ErrorResponse(String code, String message, String timestamp, String path) {
    }
}
