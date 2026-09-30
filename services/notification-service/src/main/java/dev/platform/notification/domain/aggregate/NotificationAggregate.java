package dev.platform.notification.domain.aggregate;

import java.time.OffsetDateTime;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// Cụm Notification thuần, không phụ thuộc JPA.
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class NotificationAggregate {

    private Long id;
    private UUID userId;
    private TypeValue type;
    private String title;
    private Long cardId;
    private Long boardId;
    private boolean read;
    private OffsetDateTime createdAt;

    // Loại thông báo kỳ 1. Kỳ 2 thêm PR_REVIEWED mà không sửa API cũ.
    public enum TypeValue {
        ASSIGNED, MENTIONED, MOVED
    }

    // Tạo mới từ event task-service.
    public static NotificationAggregate create(UUID userId, TypeValue type, String title,
            Long cardId, Long boardId) {
        if (userId == null) {
            throw new IllegalArgumentException("userId khong duoc null");
        }
        if (type == null) {
            throw new IllegalArgumentException("type khong duoc null");
        }
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("title khong duoc trong");
        }
        NotificationAggregate n = new NotificationAggregate();
        n.setUserId(userId);
        n.setType(type);
        n.setTitle(title.trim());
        n.setCardId(cardId);
        n.setBoardId(boardId);
        n.setRead(false);
        n.setCreatedAt(OffsetDateTime.now());
        return n;
    }

    // Đánh dấu đã đọc, đọc rồi thì giữ nguyên.
    public void markRead() {
        this.read = true;
    }
}
