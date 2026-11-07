package dev.platform.user.application.port.in;

import dev.platform.user.application.dto.UserSummaryDto;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface UserDirectoryPort {
  List<UserSummaryDto> searchUsers(String query);

  List<UserSummaryDto> getProfiles(Set<UUID> ids);
}
