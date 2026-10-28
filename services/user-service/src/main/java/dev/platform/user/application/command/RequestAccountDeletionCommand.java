package dev.platform.user.application.command;

import java.util.UUID;

public record RequestAccountDeletionCommand(UUID accountId) {
}
