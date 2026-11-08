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
  public void changeStatus(UUID id, AccountStatus newStatus) {
    Objects.requireNonNull(id, "id must not be null");
    Objects.requireNonNull(newStatus, "newStatus  must not be null");
    UserAggregate user =
        userRepository
            .findByIdForUpdate(id)
            .orElseThrow(() -> new AccountNotFoundException("Account not fould"));
    if (user.changeStatus(newStatus)) {
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
    if (user.grantRole(roleCode)) {
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
    if (user.revokeRole(roleCode)) {
      userRepository.save(user);
      accountSnapshotSyncPort.recordSystemRolesChange(
          user.getId(), user.getStatus().name(), user.getRoles(), user.getAuthVersion());
    }
  }

  @Override
  @Transactional
  public void updateRoles(UUID id, Set<String> roles) {
    Objects.requireNonNull(id, "id cannot be null");
    Objects.requireNonNull(roles, "roles cannot be null");
    UserAggregate user =
        userRepository
            .findByIdForUpdate(id)
            .orElseThrow(() -> new AccountNotFoundException("Account not found: " + id));
    if (user.updateRoles(roles)) {
      userRepository.save(user);
      accountSnapshotSyncPort.recordSystemRolesChange(
          user.getId(), user.getStatus().name(), user.getRoles(), user.getAuthVersion());
    }
  }

  @Override
  @Transactional(readOnly = true)
  public Set<String> getRoles(UUID id) {
    Objects.requireNonNull(id, "id cannot be null");
    return userRepository
        .findById(id)
        .orElseThrow(() -> new AccountNotFoundException("Account not found: " + id))
        .getRoles();
  }
}
