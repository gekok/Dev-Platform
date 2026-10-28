package dev.platform.user.application.port.in;

import dev.platform.user.application.command.RequestAccountDeletionCommand;
import dev.platform.user.application.deletion.AccountDeletionOperationView;

public interface RequestAccountDeletionPort {
    AccountDeletionOperationView request(RequestAccountDeletionCommand command);
}
