package dev.platform.user.application.service;

import dev.platform.user.application.command.LoginCommand;
import dev.platform.user.application.port.in.LoginUserPort;
import dev.platform.user.application.port.out.PasswordEncoderPort;
import dev.platform.user.application.port.out.TokenPort;
import dev.platform.user.domain.aggregate.UserAggregate;
import dev.platform.user.domain.exception.AuthenticationException;
import dev.platform.user.domain.repository.UserRepository;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LoginUserService implements LoginUserPort {
  private final UserRepository userRepository;
  private final TokenPort tokenPort;
  private final AccountAccessGuard accountAccessGuard;
  private final PasswordEncoderPort passwordEncoderPort;

  @Override
  @Transactional(readOnly = true)
  public String login(LoginCommand cmd) {
    if (cmd == null || cmd.email() == null || cmd.password() == null) {
      throw new AuthenticationException("Invalid credentials");
    }

    String normalizedEmail = cmd.email().trim().toLowerCase(Locale.ROOT);
    UserAggregate user =
        userRepository
            .findByEmail(normalizedEmail)
            .orElseThrow(() -> new AuthenticationException("Invalid credentials"));

    if (!passwordEncoderPort.matches(cmd.password(), user.getPasswordHash())) {
      throw new AuthenticationException("Invalid credentials");
    }

    try {
      accountAccessGuard.requireActive(user);
    } catch (AuthenticationException e) {
      throw new AuthenticationException("Invalid credentials");
    }
    return tokenPort.issue(user);
  }
}
