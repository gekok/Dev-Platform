package dev.platform.user.api.dto;

import dev.platform.user.application.dto.UserSummaryDto;
import java.util.UUID;

public record UserProfileResponse(
    UUID id,
    String displayName
) {
  public static UserProfileResponse from(UserSummaryDto summary) {
    return new UserProfileResponse(summary.id(), summary.fullName());
  }
}
