package dev.platform.user.application.service;

import dev.platform.user.domain.AccountStatus;
import dev.platform.user.domain.aggregate.UserAggregate;
import org.springframework.stereotype.Component;

@Component
public class AccountAccessGuard {

    public void requireActive(UserAggregate account) {
        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new IllegalStateException("Account is not active");
        }
    }
}
