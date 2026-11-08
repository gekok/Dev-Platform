package dev.platform.task.api.exception;

import java.time.OffsetDateTime;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import dev.platform.task.application.exception.AuthorizationSnapshotUnavailableException;
import dev.platform.task.application.exception.ProjectAccessDeniedException;
import dev.platform.task.application.exception.CardOperationUnavailableException;
import dev.platform.task.application.exception.CardNotFoundException;
import jakarta.servlet.http.HttpServletRequest;

// Bắt lỗi validate Aggregate, trả 400 theo kanban-api.md mục 6.
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(CardNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleCardNotFound(CardNotFoundException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse(
                "CARD_NOT_FOUND", ex.getMessage(), OffsetDateTime.now().toString(), request.getRequestURI()));
    }

    @ExceptionHandler(CardOperationUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleCardOperationUnavailable(
            CardOperationUnavailableException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(new ErrorResponse(
                "CARD_OPERATION_UNAVAILABLE", ex.getMessage(), OffsetDateTime.now().toString(), request.getRequestURI()));
    }

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
