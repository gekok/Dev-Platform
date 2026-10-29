package dev.platform.task.domain.aggregate;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// Cụm Card thuần, không phụ thuộc JPA.
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CardAggregate {

    private Long id;
    private Long projectId;
    private Long boardId;
    private Long columnId;
    private String title;
    private String description;
    private Status status = Status.TODO;
    private int position;
    private UUID assigneeId;
    private Long assigneeMembershipVersion;
    private UUID assigneeAssignedByUserId;
    @Setter(AccessLevel.NONE)
    private boolean assigneeChangeRequested;
    private OffsetDateTime dueDate;
    private List<CommentValue> comments = new ArrayList<>();

    // Trạng thái thẻ trên bảng Kanban.
    public enum Status {
        TODO, IN_PROGRESS, DONE
    }

    // Giá trị bình luận trong aggregate.
    public record CommentValue(Long id, UUID authorId, String content, OffsetDateTime createdAt) {
    }

    // Tạo thẻ mới, title bắt buộc, position >= 0.
    public static CardAggregate create(Long projectId, Long boardId, Long columnId, String title,
            String description, UUID assigneeId, Long assigneeMembershipVersion, UUID assignedByUserId,
            OffsetDateTime dueDate, int position) {
        if (projectId == null) {
            throw new IllegalArgumentException("projectId khong duoc null");
        }
        if (boardId == null) {
            throw new IllegalArgumentException("boardId khong duoc null");
        }
        if (columnId == null) {
            throw new IllegalArgumentException("columnId khong duoc null");
        }
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("title khong duoc trong");
        }
        if (position < 0) {
            throw new IllegalArgumentException("position khong duoc am");
        }
        CardAggregate card = new CardAggregate();
        card.setProjectId(projectId);
        card.setBoardId(boardId);
        card.setColumnId(columnId);
        card.setTitle(title.trim());
        card.setDescription(description);
        card.setStatus(Status.TODO);
        card.setPosition(position);
        card.setAssigneeId(assigneeId);
        card.setAssigneeMembershipVersion(assigneeMembershipVersion);
        card.setAssigneeAssignedByUserId(assignedByUserId);
        card.assigneeChangeRequested = assigneeId != null;
        card.setDueDate(dueDate);
        card.setComments(new ArrayList<>());
        return card;
    }

    // Kéo thẻ qua cột / đổi vị trí.
    public void move(Long toColumnId, int toPosition) {
        if (toColumnId == null) {
            throw new IllegalArgumentException("toColumnId khong duoc null");
        }
        if (toPosition < 0) {
            throw new IllegalArgumentException("toPosition khong duoc am");
        }
        this.columnId = toColumnId;
        this.position = toPosition;
    }

    // Gán người mới, trả true nếu đổi để bắn event.
    public boolean assign(UUID newAssignee, Long membershipVersion, UUID assignedByUserId) {
        if (Objects.equals(this.assigneeId, newAssignee)
                && Objects.equals(this.assigneeMembershipVersion, membershipVersion)) {
            return false;
        }
        this.assigneeId = newAssignee;
        this.assigneeMembershipVersion = newAssignee == null ? null : membershipVersion;
        this.assigneeAssignedByUserId = newAssignee == null ? null : assignedByUserId;
        this.assigneeChangeRequested = true;
        return true;
    }
}
