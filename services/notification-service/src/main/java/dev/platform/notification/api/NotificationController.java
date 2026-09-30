package dev.platform.notification.api;

import java.util.List;
import java.util.UUID;

import dev.platform.notification.api.dto.NotificationResponse;
import dev.platform.notification.application.command.MarkReadCommand;
import dev.platform.notification.application.port.in.ListMyNotificationsPort;
import dev.platform.notification.application.port.in.MarkReadPort;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// REST thông báo (kanban-api mục 4).
// Client chỉ được đọc và đánh dấu đã đọc, không cho tự tạo.
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final ListMyNotificationsPort listPort;
    private final MarkReadPort markReadPort;

    // 4.1 Lấy thông báo của tôi.
    @GetMapping("/me")
    public ResponseEntity<List<NotificationResponse>> listMine(
            @RequestParam(defaultValue = "false") boolean unreadOnly,
            // TODO kỳ 1: lấy userId từ JWT sub, hiện đọc header stub.
            @RequestHeader(value = "X-User-Id", required = false) UUID userId) {
        if (userId == null) {
            throw new IllegalArgumentException("Thieu X-User-Id (stub cho JWT sub)");
        }
        return ResponseEntity.ok(listPort.listMine(userId, unreadOnly).stream()
                .map(NotificationResponse::from)
                .toList());
    }

    // 4.2 Đánh dấu đã đọc (check chủ sở hữu ở service kỳ 1, sai chủ -> 403).
    @PatchMapping("/{id}/read")
    public ResponseEntity<NotificationResponse> markRead(
            @PathVariable Long id,
            // TODO kỳ 1: lấy userId từ JWT sub, hiện đọc header stub.
            @RequestHeader(value = "X-User-Id", required = false) UUID userId) {
        if (userId == null) {
            throw new IllegalArgumentException("Thieu X-User-Id (stub cho JWT sub)");
        }
        return ResponseEntity.ok(NotificationResponse.from(
                markReadPort.markRead(new MarkReadCommand(id, userId))));
    }
}
