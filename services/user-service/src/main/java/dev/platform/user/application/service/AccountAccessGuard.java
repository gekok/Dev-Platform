package dev.platform.user.application.service;

import dev.platform.user.domain.AccountStatus;
import dev.platform.user.domain.aggregate.UserAggregate;
import dev.platform.user.domain.exception.AuthenticationException;
import org.springframework.stereotype.Component;

@Component
public class AccountAccessGuard {
  public void requireActive(UserAggregate user) {
    if (user.getStatus() == AccountStatus.ACTIVE) {
      return;
    }
    switch (user.getStatus()) {
      case LOCKED -> throw new AuthenticationException("Account is locked");
      case SUSPENDED -> throw new AuthenticationException("Account is suspended");
      case INACTIVE -> throw new AuthenticationException("Account is inactive");
      default -> throw new AuthenticationException("Account is not active");
    }
  }
}
