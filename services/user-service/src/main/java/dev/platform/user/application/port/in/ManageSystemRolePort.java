package dev.platform.user.application.port.in;

import java.util.Set;
import java.util.UUID;

/**
 * Cổng vào: Quản lý gán và thu hồi System Roles (USER, SYSTEM_ADMIN).
 * Thay đổi quyền sẽ làm tăng authVersion, ghi nhận Outbox event và Snapshot sync job.
 */
public interface ManageSystemRolePort {

    void grantRole(UUID userId, String roleCode, UUID grantedBy);

    void revokeRole(UUID userId, String roleCode);

    Set<String> getRoles(UUID userId);
}
