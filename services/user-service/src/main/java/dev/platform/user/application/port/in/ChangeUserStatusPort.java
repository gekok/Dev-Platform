package dev.platform.user.application.port.in;

import dev.platform.user.domain.AccountStatus;

import java.util.UUID;

/**
 * Cổng vào: Thay đổi trạng thái tài khoản (ACTIVE <-> SUSPENDED).
 * Khi trạng thái thay đổi, hệ thống tăng authVersion và đồng thời ghi nhận
 * sự kiện Outbox cùng job đồng bộ snapshot sang Redis trong cùng transaction.
 */
public interface ChangeUserStatusPort {

    void changeStatus(UUID userId, AccountStatus newStatus);
}
