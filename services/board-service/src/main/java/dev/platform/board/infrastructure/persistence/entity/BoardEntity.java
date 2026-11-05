package dev.platform.board.infrastructure.persistence.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.time.OffsetDateTime;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.FetchType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Nationalized;

// Bảng boards (JPA), tách khỏi Aggregate thuần.
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "boards", uniqueConstraints = @UniqueConstraint(name = "uq_boards_project_key", columnNames = {"project_id", "board_key"}))
public class BoardEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", insertable = false, updatable = false)
    private ProjectEntity project;

    @Nationalized
    @Column(nullable = false, length = 255)
    private String name;

    @Column(name = "board_key", nullable = false, length = 20)
    private String boardKey;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "created_by_user_id", nullable = false, columnDefinition = "uniqueidentifier")
    private UUID createdByUserId;

    @Column(name = "created_at", nullable = false, columnDefinition = "datetimeoffset")
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false, columnDefinition = "datetimeoffset")
    private OffsetDateTime updatedAt;

    @Builder.Default
    @OneToMany(mappedBy = "board", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC")
    private List<BoardColumnEntity> columns = new ArrayList<>();
}
