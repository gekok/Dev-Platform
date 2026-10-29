package dev.platform.notification.api.internal;

import dev.platform.notification.api.dto.CreateNotificationRequest;
import dev.platform.notification.api.dto.NotificationResponse;
import dev.platform.notification.application.command.CreateNotificationCommand;
import dev.platform.notification.application.port.in.CreateNotificationPort;
import dev.platform.notification.domain.aggregate.NotificationAggregate.TypeValue;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// API skeleton cho task-service tạo ASSIGNED / MENTIONED / MOVED.
// Chưa có service-to-service authentication; endpoint bị tắt mặc định.
@RestController
@RequestMapping("/internal/v1/notifications")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "dev-platform.internal-notifications.enabled", havingValue = "true")
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
                        req.userId(), req.projectId(), type.name(), req.title(), req.cardId(), req.boardId()))));
    }
}
