package dev.platform.notification.infrastructure.persistence.entity;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// Bảng notifications (JPA), tách khỏi Aggregate thuần.
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "notifications", indexes = {
        @Index(name = "idx_notifications_user_read", columnList = "user_id, read")
})
public class NotificationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, columnDefinition = "uuid")
    private UUID userId;

    @Column(name = "project_id")
    private Long projectId;

    @Column(nullable = false, length = 30)
    @Builder.Default
    private String type = "ASSIGNED";

    @Column(nullable = false, length = 500)
    private String title;

    @Column(name = "card_id")
    private Long cardId;

    @Column(name = "board_id")
    private Long boardId;

    @Column(nullable = false)
    @Builder.Default
    private boolean read = false;

    @Column(name = "created_at", columnDefinition = "timestamptz")
    private OffsetDateTime createdAt;
}
