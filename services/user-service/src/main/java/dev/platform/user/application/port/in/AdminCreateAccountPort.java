package dev.platform.user.application.port.in;

import dev.platform.user.application.command.AdminCreateAccountCommand;
import dev.platform.user.application.dto.UserSummaryDto;

public interface AdminCreateAccountPort {
  UserSummaryDto createAccount(AdminCreateAccountCommand command);
}
