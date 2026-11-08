package dev.platform.board.api.exception;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

import dev.platform.board.domain.aggregate.ProjectRuleViolation;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.dao.OptimisticLockingFailureException;

// Lỗi chuẩn {code,message,timestamp,path} (kanban-api mục 6).
@RestControllerAdvice
public class GlobalExceptionHandler {

    // Validate @Valid lỗi -> 400.
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(
            MethodArgumentNotValidException ex, HttpServletRequest req) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .findFirst()
                .orElse("Request validation failed.");
        return projectBody(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message, req);
    }

    @ExceptionHandler(ProjectRuleViolation.class)
    public ResponseEntity<Map<String, Object>> handleProjectRuleViolation(
            ProjectRuleViolation ex, HttpServletRequest req) {
        return projectBody(HttpStatus.valueOf(ex.httpStatus()), ex.code(), ex.getMessage(), req);
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<Map<String, Object>> handleOptimisticLock(
            OptimisticLockingFailureException ex, HttpServletRequest req) {
        return projectBody(HttpStatus.CONFLICT, "VERSION_CONFLICT",
                "The resource changed concurrently; reload and retry.", req);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> handleDataConflict(
            DataIntegrityViolationException ex, HttpServletRequest req) {
        String details = ex.getMostSpecificCause().getMessage();
        String code = details != null && (details.toLowerCase().contains("ux_invitations_pending_unique")
                || details.toLowerCase().contains("ux_invitations_pending"))
                ? "INVITATION_ALREADY_PENDING"
                : "DATA_CONFLICT";
        return projectBody(HttpStatus.CONFLICT, code, "The request conflicts with existing data.", req);
    }

    // Validate tay trong Aggregate -> 400.
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(
            IllegalArgumentException ex, HttpServletRequest req) {
        return body(HttpStatus.BAD_REQUEST, "BAD_REQUEST", ex.getMessage(), req);
    }

    // Không thấy bảng/cột -> 404.
    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(
            NoSuchElementException ex, HttpServletRequest req) {
        return body(HttpStatus.NOT_FOUND, "BOARD_NOT_FOUND", ex.getMessage(), req);
    }

    // Trùng key bảng -> 409.
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleConflict(
            IllegalStateException ex, HttpServletRequest req) {
        return body(HttpStatus.CONFLICT, "BOARD_KEY_CONFLICT", ex.getMessage(), req);
    }

    private ResponseEntity<Map<String, Object>> body(
            HttpStatus status, String code, String message, HttpServletRequest req) {
        return ResponseEntity.status(status).body(Map.of(
                "code", code,
                "message", message == null ? status.getReasonPhrase() : message,
                "timestamp", OffsetDateTime.now().toString(),
                "path", req.getRequestURI()));
    }

    private ResponseEntity<Map<String, Object>> projectBody(
            HttpStatus status, String code, String message, HttpServletRequest req) {
        return ResponseEntity.status(status).body(Map.of(
                "error", Map.of(
                        "code", code,
                        "message", message == null ? status.getReasonPhrase() : message,
                        "details", List.of()),
                "meta", Map.of(
                        "correlationId", correlationId(req),
                        "timestamp", OffsetDateTime.now().toString())));
    }

    private String correlationId(HttpServletRequest request) {
        String value = request.getHeader("X-Correlation-Id");
        return value == null || value.isBlank() ? UUID.randomUUID().toString() : value;
    }
}
