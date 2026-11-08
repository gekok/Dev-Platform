package dev.platform.user.application.service;

import dev.platform.user.application.dto.UserSummaryDto;
import dev.platform.user.application.port.in.GetCurrentUserPort;
import dev.platform.user.domain.aggregate.UserAggregate;
import dev.platform.user.domain.exception.AccountNotFoundException;
import dev.platform.user.domain.repository.UserRepository;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GetCurrentUserService implements GetCurrentUserPort {
  private final UserRepository userRepository;
  private final AccountAccessGuard accountAccessGuard;

  @Override
  @Transactional(readOnly = true)
  public UserSummaryDto getCurrentUser(UUID accountId) {
    Objects.requireNonNull(accountId, "accountId cannot be null");

    UserAggregate user =
        userRepository
            .findById(accountId)
            .orElseThrow(() -> new AccountNotFoundException("Account not found"));
    accountAccessGuard.requireActive(user);
    return UserSummaryDto.from(user);
  }
}
