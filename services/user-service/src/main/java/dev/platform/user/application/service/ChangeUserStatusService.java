package dev.platform.user.application.service;

import dev.platform.user.application.port.in.ChangeUserStatusPort;
import dev.platform.user.application.port.in.ManageSystemRolePort;
import dev.platform.user.application.port.out.AccountSnapshotSyncPort;
import dev.platform.user.domain.AccountStatus;
import dev.platform.user.domain.aggregate.UserAggregate;
import dev.platform.user.domain.exception.AuthenticationException;
import dev.platform.user.domain.repository.UserRepository;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.util.Snapshottable;

@Service
@RequiredArgsConstructor
public class ChangeUserStatusService implements ChangeUserStatusPort, ManageSystemRolePort {
  private final UserRepository userRepository;
  private final AccountSnapshotSyncPort accountSnapshotSyncPort;
  private final Snapshottable snapshottable;

  @Override
  @Transactional
  public void changeStatus(UUID id, AccountStatus newStatus) {
    Objects.requireNonNull(id, "id cannot be null");
    Objects.requireNonNull(newStatus, "newStatus cannot be null");
    UserAggregate user =
        userRepository
            .findByIdForUpdate(id)
            .orElseThrow(() -> new AuthenticationException("User not found" + id));
    boolean change = user.changeStatus(newStatus);
    if (change) {
      userRepository.save(user);
      accountSnapshotSyncPort.recordStatusChange(
          user.getId(), user.getStatus().name(), user.getRoles(), user.getAuthVersion());
    }
  }

  @Override
  @Transactional
  public void grantRole(UUID id, String roleCode) {
    Objects.requireNonNull(id, "id cannot be null");
    Objects.requireNonNull(roleCode, "roleCode cannot be null");
    UserAggregate user =
        userRepository
            .findByIdForUpdate(id)
            .orElseThrow(() -> new AuthenticationException("User not found" + id));
    boolean change = user.grantRole(roleCode);
    if (change) {
      userRepository.save(user);
      accountSnapshotSyncPort.recordStatusChange(
          user.getId(), user.getStatus().name(), user.getRoles(), user.getAuthVersion());
    }
  }

  @Override
  public void revokeRole(UUID id, String roleCode) {
    Objects.requireNonNull(id, "id cannot be null");
    Objects.requireNonNull(roleCode, "roleCode cannot be null");
    UserAggregate user =
        userRepository
            .findByIdForUpdate(id)
            .orElseThrow(() -> new AuthenticationException("User not found" + id));
    boolean change = user.revokeRole(roleCode);
    if (change) {
      userRepository.save(user);
      accountSnapshotSyncPort.recordStatusChange(
          user.getId(), user.getStatus().name(), user.getRoles(), user.getAuthVersion());
    }
  }

  @Override
  @Transactional(readOnly = true)
  public Set<String> getRoles(UUID id) {
    Objects.requireNonNull(id, "id cannot be null");
    UserAggregate user =
        userRepository
            .findById(id)
            .orElseThrow(() -> new AuthenticationException("User not found" + id));
    return user.getRoles();
  }
}
