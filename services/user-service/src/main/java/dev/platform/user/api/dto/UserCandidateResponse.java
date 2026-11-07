package dev.platform.user.api.dto;

import dev.platform.user.application.dto.UserSummaryDto;
import java.util.UUID;

public record UserCandidateResponse(
    UUID id,
    String fullName,
    String email
) {
  public static UserCandidateResponse from(UserSummaryDto summary) {
    return new UserCandidateResponse(summary.id(), summary.fullName(), summary.email());
  }
}
