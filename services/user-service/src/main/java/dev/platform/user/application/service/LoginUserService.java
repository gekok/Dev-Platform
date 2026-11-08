package dev.platform.user.application.service;

import dev.platform.user.application.command.LoginCommand;
import dev.platform.user.application.port.in.LoginUserPort;
import dev.platform.user.application.port.out.PasswordEncoderPort;
import dev.platform.user.application.port.out.TokenPort;
import dev.platform.user.domain.aggregate.UserAggregate;
import dev.platform.user.domain.exception.AuthenticationException;
import java.util.Locale;
import dev.platform.user.domain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LoginUserService implements LoginUserPort {
  private static final String DUMMY_BCRYPT_HASH =
      "$2a$12$BqfnOOfzIFaiMsWqu2MAk.lferAigH2XTucXueq8o2WQ6jDVwFm9W";

  private final UserRepository userRepository;
  private final AccountAccessGuard accountAccessGuard;
  private final PasswordEncoderPort passwordEncoderPort;
  private final TokenPort tokenPort;

  @Override
  @Transactional(readOnly = true)
  public String login(LoginCommand cmd) {
    if (cmd == null || cmd.email() == null || cmd.password() == null) {
      throw new AuthenticationException("Invalid credentials");
    }
    String normalizedEmail = cmd.email().trim().toLowerCase(Locale.ROOT);
    UserAggregate user = userRepository.findByEmail(normalizedEmail).orElse(null);

    if (user == null) {
      // Mitigate timing attack: compute bcrypt hash so response time is indistinguishable
      passwordEncoderPort.matches(cmd.password(), DUMMY_BCRYPT_HASH);
      throw new AuthenticationException("Invalid credentials");
    }

    if (!passwordEncoderPort.matches(cmd.password(), user.getPasswordHash())) {
      throw new AuthenticationException("Invalid credentials");
    }
    try {
      accountAccessGuard.requireActive(user);
    } catch (AuthenticationException exception) {
      throw new AuthenticationException("Invalid credentials");
    }

    return tokenPort.issue(user);
  }
}
