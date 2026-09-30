package dev.platform.notification.api.internal;

import dev.platform.notification.api.dto.CreateNotificationRequest;
import dev.platform.notification.api.dto.NotificationResponse;
import dev.platform.notification.application.command.CreateNotificationCommand;
import dev.platform.notification.application.port.in.CreateNotificationPort;
import dev.platform.notification.domain.aggregate.NotificationAggregate.TypeValue;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// API nội bộ cho task-service gọi sang kỳ 1 (tạo ASSIGNED / MENTIONED).
// Không qua gateway, chỉ gọi trong cụm bằng service token.
@RestController
@RequestMapping("/internal/v1/notifications")
@RequiredArgsConstructor
public class InternalNotificationController {

    private final CreateNotificationPort createPort;

    @PostMapping
    public ResponseEntity<NotificationResponse> create(
            @Valid @RequestBody CreateNotificationRequest req) {
        TypeValue type;
        try {
            type = TypeValue.valueOf(req.type());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("type phai la ASSIGNED, MENTIONED, MOVED");
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(NotificationResponse.from(
                createPort.create(new CreateNotificationCommand(
                        req.userId(), type.name(), req.title(), req.cardId(), req.boardId()))));
    }
}
