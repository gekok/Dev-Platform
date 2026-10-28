package dev.platform.user.application.port.in;

import dev.platform.user.application.deletion.AccountDeletionOperationView;

import java.util.Optional;
import java.util.UUID;

public interface GetAccountDeletionOperationPort {
    Optional<AccountDeletionOperationView> get(UUID operationId);
}
