package dev.platform.task.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Gốc cụm Card (aggregate root, còn gọi là task).
 * boardId, columnId chỉ là số tham chiếu sang board-service.
 * assigneeId chỉ là UUID tham chiếu sang user-service.
 * Không tạo FK JPA chéo database.
 *
 * @deprecated Kỳ 1 thay bằng domain/aggregate/CardAggregate thuần
 *             + infrastructure/persistence/entity/CardEntity JPA.
 */
@Deprecated
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "cards")
public class Card {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "board_id", nullable = false)
    private Long boardId;

    @Column(name = "column_id", nullable = false)
    private Long columnId;

    @Column(nullable = false, length = 500)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    @Builder.Default
    private CardStatus status = CardStatus.TODO;

    @Column(nullable = false)
    @Builder.Default
    private int position = 0;

    @Column(name = "assignee_id", columnDefinition = "uuid")
    private UUID assigneeId;

    @Column(name = "due_date", columnDefinition = "timestamptz")
    private OffsetDateTime dueDate;

    @Builder.Default
    @OneToMany(mappedBy = "card", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("createdAt ASC")
    private List<CardComment> comments = new ArrayList<>();
}
