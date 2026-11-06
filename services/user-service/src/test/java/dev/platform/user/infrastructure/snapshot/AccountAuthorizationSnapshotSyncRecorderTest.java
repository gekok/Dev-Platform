package dev.platform.user.infrastructure.snapshot;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.platform.contracts.EventEnvelope;
import dev.platform.contracts.EventTypes;
import dev.platform.shared.messaging.OutboxEventWriter;
import dev.platform.user.infrastructure.persistence.entity.AccountAuthorizationSnapshotSyncJobEntity;
import dev.platform.user.infrastructure.persistence.repository.AccountAuthorizationSnapshotSyncJobJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class AccountAuthorizationSnapshotSyncRecorderTest {

    private AccountAuthorizationSnapshotSyncJobJpaRepository jobs;
    private OutboxEventWriter outbox;
    private ObjectMapper objectMapper;
    private AccountAuthorizationSnapshotSyncRecorder recorder;

    @BeforeEach
    void setUp() {
        jobs = mock(AccountAuthorizationSnapshotSyncJobJpaRepository.class);
        outbox = mock(OutboxEventWriter.class);
        objectMapper = new ObjectMapper();
        recorder = new AccountAuthorizationSnapshotSyncRecorder(jobs, outbox, objectMapper);
    }

    @Test
    @DisplayName("recordStatusChange: Lưu job đồng bộ snapshot và ghi nhận sự kiện Outbox user.status.changed")
    void shouldSaveSnapshotSyncJobAndAppendOutboxEventOnStatusChange() {
        UUID accountId = UUID.randomUUID();

        recorder.recordStatusChange(accountId, "SUSPENDED", Set.of("USER"), 2L);

        // Kiểm tra lưu job
        ArgumentCaptor<AccountAuthorizationSnapshotSyncJobEntity> jobCaptor =
                ArgumentCaptor.forClass(AccountAuthorizationSnapshotSyncJobEntity.class);
        verify(jobs).save(jobCaptor.capture());
        AccountAuthorizationSnapshotSyncJobEntity savedJob = jobCaptor.getValue();
        assertThat(savedJob.getAccountId()).isEqualTo(accountId);
        assertThat(savedJob.getAuthVersion()).isEqualTo(2L);
        assertThat(savedJob.getSnapshotJson()).contains("\"status\":\"SUSPENDED\"");
        assertThat(savedJob.getSnapshotJson()).contains("\"authVersion\":2");

        // Kiểm tra append outbox
        @SuppressWarnings("rawtypes")
        ArgumentCaptor<EventEnvelope> outboxCaptor = ArgumentCaptor.forClass(EventEnvelope.class);
        verify(outbox).append(outboxCaptor.capture());
        @SuppressWarnings("unchecked")
        EventEnvelope<?> envelope = outboxCaptor.getValue();
        assertThat(envelope.eventType()).isEqualTo(EventTypes.USER_STATUS_CHANGED);
        assertThat(envelope.aggregateId()).isEqualTo(accountId.toString());
        assertThat(envelope.aggregateVersion()).isEqualTo(2L);
    }

    @Test
    @DisplayName("recordSystemRolesChange: Lưu job đồng bộ snapshot và ghi nhận sự kiện Outbox user.system-roles.changed")
    void shouldSaveSnapshotSyncJobAndAppendOutboxEventOnRolesChange() {
        UUID accountId = UUID.randomUUID();

        recorder.recordSystemRolesChange(accountId, "ACTIVE", Set.of("USER", "SYSTEM_ADMIN"), 3L);

        // Kiểm tra lưu job
        ArgumentCaptor<AccountAuthorizationSnapshotSyncJobEntity> jobCaptor =
                ArgumentCaptor.forClass(AccountAuthorizationSnapshotSyncJobEntity.class);
        verify(jobs).save(jobCaptor.capture());
        AccountAuthorizationSnapshotSyncJobEntity savedJob = jobCaptor.getValue();
        assertThat(savedJob.getAccountId()).isEqualTo(accountId);
        assertThat(savedJob.getAuthVersion()).isEqualTo(3L);
        assertThat(savedJob.getSnapshotJson()).contains("SYSTEM_ADMIN");

        // Kiểm tra append outbox
        @SuppressWarnings("rawtypes")
        ArgumentCaptor<EventEnvelope> outboxCaptor = ArgumentCaptor.forClass(EventEnvelope.class);
        verify(outbox).append(outboxCaptor.capture());
        @SuppressWarnings("unchecked")
        EventEnvelope<?> envelope = outboxCaptor.getValue();
        assertThat(envelope.eventType()).isEqualTo(EventTypes.USER_SYSTEM_ROLES_CHANGED);
        assertThat(envelope.aggregateId()).isEqualTo(accountId.toString());
        assertThat(envelope.aggregateVersion()).isEqualTo(3L);
    }
}
