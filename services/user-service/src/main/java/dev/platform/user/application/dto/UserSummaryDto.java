package dev.platform.user.application.dto;

import dev.platform.user.domain.AccountStatus;
import dev.platform.user.domain.aggregate.UserAggregate;
import java.util.Set;
import java.util.UUID;

public record UserSummaryDto(
    UUID id,
    String email,
    String fullName,
    AccountStatus status,
    long authVersion,
    Set<String> roles) {
  public static UserSummaryDto from(UserAggregate user) {
    return new UserSummaryDto(
        user.getId(),
        user.getEmail(),
        user.getFullName(),
        user.getStatus(),
        user.getAuthVersion(),
        user.getRoles());
  }
}
