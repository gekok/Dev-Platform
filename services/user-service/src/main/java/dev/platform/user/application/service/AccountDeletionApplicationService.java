package dev.platform.user.application.service;

import dev.platform.contracts.EventEnvelope;
import dev.platform.contracts.EventTypes;
import dev.platform.contracts.event.AccountDeletionRequested;
import dev.platform.user.application.command.RequestAccountDeletionCommand;
import dev.platform.user.application.deletion.AccountDeletionOperationView;
import dev.platform.user.application.port.in.GetAccountDeletionOperationPort;
import dev.platform.user.application.port.in.RequestAccountDeletionPort;
import dev.platform.user.application.port.out.AccountDeletionStorePort;
import dev.platform.shared.messaging.OutboxEventWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AccountDeletionApplicationService implements RequestAccountDeletionPort, GetAccountDeletionOperationPort {

    private final AccountDeletionStorePort store;
    private final OutboxEventWriter outboxEventWriter;

    @Override
    @Transactional
    public AccountDeletionOperationView request(RequestAccountDeletionCommand command) {
        UUID operationId = UUID.randomUUID();
        AccountDeletionOperationView operation = store.start(command.accountId(), operationId);
        EventEnvelope<AccountDeletionRequested> event = new EventEnvelope<>(
                UUID.randomUUID(),
                EventTypes.ACCOUNT_DELETION_REQUESTED,
                1,
                "ACCOUNT_DELETION_OPERATION",
                operationId.toString(),
                1,
                OffsetDateTime.now(),
                new AccountDeletionRequested(operationId, command.accountId(), "ADMIN_REQUEST"));
        outboxEventWriter.append(event);
        return operation;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AccountDeletionOperationView> get(UUID operationId) {
        return store.find(operationId);
    }
}
