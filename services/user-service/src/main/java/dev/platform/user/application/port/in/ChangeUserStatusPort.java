package dev.platform.user.application.port.in;

import dev.platform.user.domain.AccountStatus;
import java.util.UUID;

public interface ChangeUserStatusPort {
  void changeStatus(UUID id, AccountStatus newStatus);
}
