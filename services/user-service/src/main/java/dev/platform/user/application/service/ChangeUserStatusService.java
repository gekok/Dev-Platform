package dev.platform.user.application.service;

import dev.platform.user.application.port.in.ChangeUserStatusPort;
import dev.platform.user.application.port.in.ManageSystemRolePort;
import dev.platform.user.application.port.out.AccountSnapshotSyncPort;
import dev.platform.user.domain.AccountStatus;
import dev.platform.user.domain.aggregate.UserAggregate;
import dev.platform.user.domain.exception.AccountNotFoundException;
import dev.platform.user.domain.repository.UserRepository;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ChangeUserStatusService implements ChangeUserStatusPort, ManageSystemRolePort {

  private final UserRepository userRepository;
  private final AccountSnapshotSyncPort accountSnapshotSyncPort;

  @Override
  @Transactional
  public void changeStatus(UUID id, AccountStatus status) {
    Objects.requireNonNull(id, "id cannot be null");
    Objects.requireNonNull(status, "status cannot be null");
    UserAggregate user =
        userRepository
            .findByIdForUpdate(id)
            .orElseThrow(() -> new AccountNotFoundException("Account not found: " + id));
    boolean change = user.changeStatus(status);
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
            .orElseThrow(() -> new AccountNotFoundException("Account not found: " + id));
    boolean change = user.grantRole(roleCode);
    if (change) {
      userRepository.save(user);
      accountSnapshotSyncPort.recordSystemRolesChange(
          user.getId(), user.getStatus().name(), user.getRoles(), user.getAuthVersion());
    }
  }

  @Override
  @Transactional
  public void revokeRole(UUID id, String roleCode) {
    Objects.requireNonNull(id, "id cannot be null");
    Objects.requireNonNull(roleCode, "roleCode cannot be null");
    UserAggregate user =
        userRepository
            .findByIdForUpdate(id)
            .orElseThrow(() -> new AccountNotFoundException("Account not found: " + id));
    boolean change = user.revokeRole(roleCode);
    if (change) {
      userRepository.save(user);
      accountSnapshotSyncPort.recordSystemRolesChange(
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
            .orElseThrow(() -> new AccountNotFoundException("Account not found: " + id));
    return user.getRoles();
  }
}
