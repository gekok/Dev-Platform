package dev.platform.user.application.port.in;

import dev.platform.user.application.dto.UserSummaryDto;
import java.util.UUID;

public interface GetCurrentUserPort {
  UserSummaryDto getCurrentUser(UUID userId);
}
