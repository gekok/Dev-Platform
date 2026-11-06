package dev.platform.user.application.service;

import dev.platform.user.application.port.in.ChangeUserStatusPort;
import dev.platform.user.application.port.in.ManageSystemRolePort;
import dev.platform.user.domain.AccountStatus;
import dev.platform.user.domain.aggregate.UserAggregate;
import dev.platform.user.domain.repository.UserRepository;
import dev.platform.user.infrastructure.snapshot.AccountAuthorizationSnapshotSyncRecorder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;

/**
 * Service xử lý thay đổi trạng thái tài khoản và System Roles.
 * Đảm bảo tính nguyên tử: Mỗi thay đổi Account/status/role đều tăng authVersion,
 * commit dữ liệu, lưu Outbox event và Snapshot sync job trong CÙNG một transaction.
 */
@Service
@RequiredArgsConstructor
public class ChangeUserStatusService implements ChangeUserStatusPort, ManageSystemRolePort {

    private final UserRepository users;
    private final AccountAuthorizationSnapshotSyncRecorder snapshotSyncRecorder;

    @Transactional
    @Override
    public void changeStatus(UUID userId, AccountStatus newStatus) {
        UserAggregate user = users.findById(userId)
                .orElseThrow(() -> new NoSuchElementException("User not found: " + userId));

        boolean changed = user.changeStatus(newStatus);
        if (changed) {
            users.save(user);
            snapshotSyncRecorder.recordStatusChange(
                    user.getId(),
                    user.getStatus().name(),
                    user.getSystemRoles(),
                    user.getAuthVersion()
            );
        }
    }

    @Transactional
    @Override
    public void grantRole(UUID userId, String roleCode, UUID grantedBy) {
        UserAggregate user = users.findById(userId)
                .orElseThrow(() -> new NoSuchElementException("User not found: " + userId));

        boolean changed = user.grantRole(roleCode);
        if (changed) {
            users.save(user);
            snapshotSyncRecorder.recordSystemRolesChange(
                    user.getId(),
                    user.getStatus().name(),
                    user.getSystemRoles(),
                    user.getAuthVersion()
            );
        }
    }

    @Transactional
    @Override
    public void revokeRole(UUID userId, String roleCode) {
        UserAggregate user = users.findById(userId)
                .orElseThrow(() -> new NoSuchElementException("User not found: " + userId));

        boolean changed = user.revokeRole(roleCode);
        if (changed) {
            users.save(user);
            snapshotSyncRecorder.recordSystemRolesChange(
                    user.getId(),
                    user.getStatus().name(),
                    user.getSystemRoles(),
                    user.getAuthVersion()
            );
        }
    }

    @Transactional(readOnly = true)
    @Override
    public Set<String> getRoles(UUID userId) {
        UserAggregate user = users.findById(userId)
                .orElseThrow(() -> new NoSuchElementException("User not found: " + userId));
        return user.getSystemRoles();
    }
}
