package dev.platform.notification.infrastructure.event;

import org.springframework.stereotype.Component;

// Lắng nghe event từ task-service (gán việc, mention).
// Kỳ 1 nối qua REST internal hoặc queue, hiện giữ chỗ biên dịch.
@Component
public class TaskEventListener {

    // TODO kỳ 1: nhận onCardAssigned(cardId, assigneeId) rồi gọi CreateNotificationPort.
    // TODO kỳ 1: nhận onMentioned(cardId, mentionedUserIds) rồi tạo MENTIONED.
}
