package dev.platform.notification.infrastructure.adapter;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

import dev.platform.notification.application.port.out.SaveNotificationPort;
import dev.platform.notification.domain.aggregate.NotificationAggregate;
import dev.platform.notification.domain.aggregate.NotificationAggregate.TypeValue;
import dev.platform.notification.infrastructure.persistence.entity.NotificationEntity;
import dev.platform.notification.infrastructure.persistence.repository.NotificationJpaRepository;
import lombok.RequiredArgsConstructor;

// Adapter JPA cho cổng ra SaveNotificationPort.
@Component
@RequiredArgsConstructor
public class SaveNotificationAdapter implements SaveNotificationPort {

    private final NotificationJpaRepository jpa;

    @Override
    public NotificationAggregate save(NotificationAggregate n) {
        return toAggregate(jpa.save(toEntity(n)));
    }

    @Override
    public Optional<NotificationAggregate> findById(Long id) {
        return jpa.findById(id).map(this::toAggregate);
    }

    @Override
    public List<NotificationAggregate> findByUserIdOrderByCreatedAtDesc(UUID userId) {
        return jpa.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(this::toAggregate)
                .toList();
    }

    @Override
    public List<NotificationAggregate> findByUserIdAndReadFalse(UUID userId) {
        return jpa.findByUserIdAndReadFalseOrderByCreatedAtDesc(userId).stream()
                .map(this::toAggregate)
                .toList();
    }

    // Chuyển Aggregate sang Entity để lưu.
    private NotificationEntity toEntity(NotificationAggregate n) {
        return NotificationEntity.builder()
                .id(n.getId())
                .userId(n.getUserId())
                .type(n.getType() == null ? "ASSIGNED" : n.getType().name())
                .title(n.getTitle())
                .cardId(n.getCardId())
                .boardId(n.getBoardId())
                .read(n.isRead())
                .createdAt(n.getCreatedAt() == null ? OffsetDateTime.now() : n.getCreatedAt())
                .build();
    }

    // Chuyển Entity sang Aggregate để trả về.
    private NotificationAggregate toAggregate(NotificationEntity e) {
        return new NotificationAggregate(
                e.getId(),
                e.getUserId(),
                parseType(e.getType()),
                e.getTitle(),
                e.getCardId(),
                e.getBoardId(),
                e.isRead(),
                e.getCreatedAt());
    }

    // Parse type string, lạ thì về ASSIGNED.
    private TypeValue parseType(String raw) {
        if (raw == null) {
            return TypeValue.ASSIGNED;
        }
        try {
            return TypeValue.valueOf(raw);
        } catch (IllegalArgumentException ex) {
            return TypeValue.ASSIGNED;
        }
    }
}
