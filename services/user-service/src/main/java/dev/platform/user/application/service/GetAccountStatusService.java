package dev.platform.user.application.service;

import dev.platform.user.application.port.in.GetAccountStatusPort;
import dev.platform.user.domain.AccountStatus;
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
public class GetAccountStatusService implements GetAccountStatusPort {
  private final UserRepository userRepository;

  @Override
  @Transactional(readOnly = true)
  public AccountStatus getAccountStatus(UUID accountId) {
    Objects.requireNonNull(accountId, "accountId cannot be null");
    UserAggregate user =
        userRepository
            .findById(accountId)
            .orElseThrow(() -> new AccountNotFoundException("Account not found"));
    return user.getStatus();
  }
}
