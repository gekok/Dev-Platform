package dev.platform.task.api.dto;

import java.util.List;
import java.util.UUID;

// Body bình luận (kanban-api.md 3.5).
public record AddCommentRequest(
        String content,
        List<UUID> mentionedUserIds
) {
}
