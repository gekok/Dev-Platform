package dev.platform.board.infrastructure.persistence.entity;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

// Bảng boards (JPA), tách khỏi Aggregate thuần.
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "boards")
public class BoardEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(name = "board_key", nullable = false, unique = true, length = 20)
    private String boardKey;

    @Column(name = "owner_id", nullable = false, columnDefinition = "uuid")
    private UUID ownerId;

    @Builder.Default
    @OneToMany(mappedBy = "board", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC")
    private List<BoardColumnEntity> columns = new ArrayList<>();

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

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