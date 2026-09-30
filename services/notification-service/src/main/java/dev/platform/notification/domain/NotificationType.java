package dev.platform.notification.domain;

/**
 * Loại thông báo. Kỳ 2 thêm type mới như PR_REVIEWED mà không sửa API cũ.
 */
public enum NotificationType {
    ASSIGNED, MENTIONED, MOVED
}
