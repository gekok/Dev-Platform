package dev.platform.user.application.port.out;

import dev.platform.user.application.deletion.AccountDeletionOperationView;

import java.util.Optional;
import java.util.UUID;

public interface AccountDeletionStorePort {
    AccountDeletionOperationView start(UUID accountId, UUID operationId);

    Optional<AccountDeletionOperationView> find(UUID operationId);

    void complete(UUID operationId, UUID accountId);
}
