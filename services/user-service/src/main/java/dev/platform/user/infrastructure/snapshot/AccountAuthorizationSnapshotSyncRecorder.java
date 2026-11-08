package dev.platform.user.infrastructure.snapshot;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.platform.contracts.EventEnvelope;
import dev.platform.contracts.EventTypes;
import dev.platform.contracts.event.UserStatusChanged;
import dev.platform.contracts.event.UserSystemRolesChanged;
import dev.platform.shared.messaging.OutboxEventWriter;
import dev.platform.user.application.port.out.AccountSnapshotSyncPort;
import dev.platform.user.infrastructure.persistence.entity.AccountAuthorizationSnapshotSyncJobEntity;
import dev.platform.user.infrastructure.persistence.repository.AccountAuthorizationSnapshotSyncJobJpaRepository;
import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class AccountAuthorizationSnapshotSyncRecorder implements AccountSnapshotSyncPort {

  private final AccountAuthorizationSnapshotSyncJobJpaRepository jobs;
  private final OutboxEventWriter outbox;
  private final ObjectMapper objectMapper;

  @Override
  @Transactional
  public void recordStatusChange(UUID accountId, String status, Set<String> roles, long authVersion) {
    UserStatusChanged payload = new UserStatusChanged(accountId, status, authVersion);
    record(accountId, status, roles, authVersion, EventTypes.USER_STATUS_CHANGED, payload);
  }

  @Override
  @Transactional
  public void recordSystemRolesChange(UUID accountId, String status, Set<String> roles, long authVersion) {
    UserSystemRolesChanged payload = new UserSystemRolesChanged(accountId, roles, authVersion);
    record(accountId, status, roles, authVersion, EventTypes.USER_SYSTEM_ROLES_CHANGED, payload);
  }

  private void record(
      UUID accountId,
      String status,
      Set<String> roles,
      long authVersion,
      String eventType,
      Object payload) {
    OffsetDateTime now = OffsetDateTime.now();
    AccountAuthorizationSnapshot snapshot =
        new AccountAuthorizationSnapshot(accountId, status, Set.copyOf(roles), authVersion);

    try {
      jobs.save(new AccountAuthorizationSnapshotSyncJobEntity(
          UUID.randomUUID(), accountId, authVersion, objectMapper.writeValueAsString(snapshot), now));
    } catch (JsonProcessingException exception) {
      throw new IllegalArgumentException("Could not serialize Account snapshot " + accountId, exception);
    }

    outbox.append(new EventEnvelope<>(
        UUID.randomUUID(), eventType, 1, "USER_ACCOUNT", accountId.toString(), authVersion, now, payload));
  }
}
