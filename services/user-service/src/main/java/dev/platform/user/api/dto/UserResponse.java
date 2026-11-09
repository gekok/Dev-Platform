package dev.platform.user.api.dto;

import dev.platform.user.application.dto.UserSummaryDto;
import dev.platform.user.domain.AccountStatus;
import java.util.Set;
import java.util.UUID;

public record UserResponse(
    UUID id,
    String email,
    String fullName,
    AccountStatus status,
    long authVersion,
    Set<String> roles
) {
  public static UserResponse from(UserSummaryDto dto) {
    return new UserResponse(
        dto.id(),
        dto.email(),
        dto.fullName(),
        dto.status(),
        dto.authVersion(),
        dto.roles()
    );
  }
}
