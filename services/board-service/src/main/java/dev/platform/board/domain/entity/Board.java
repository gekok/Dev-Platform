package dev.platform.board.domain.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Gốc cụm Board (aggregate root).
 * Giữ bảng và cột. Không giữ thẻ chi tiết — thẻ sống ở task-service.
 * ownerId chỉ là UUID tham chiếu sang user-service, không FK chéo DB.
 *
 * @deprecated Kỳ 1 thay bằng Aggregate + infra entity.
 * Dùng domain.aggregate.BoardAggregate cho logic thuần
 * và infrastructure.persistence.entity.BoardEntity cho JPA.
 */
@Deprecated
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "boards")
public class Board {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(name = "board_key", nullable = false, unique = true, length = 20)
    private String key;

    @Column(name = "owner_id", nullable = false, columnDefinition = "uuid")
    private UUID ownerId;

    @Builder.Default
    @OneToMany(mappedBy = "board", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC")
    private List<BoardColumn> columns = new ArrayList<>();

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private dev.platform.board.domain.entity.Project project;

    @Size(max = 20)
    @NotNull
    @ColumnDefault("'ACTIVE'")
    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @NotNull
    @Column(name = "created_by_user_id", nullable = false)
    private UUID createdByUserId;

    @NotNull
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @NotNull
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

}
