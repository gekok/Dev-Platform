package dev.platform.user.infrastructure.security;

import dev.platform.user.application.policy.BcryptPasswordPolicy;
import dev.platform.user.application.port.out.PasswordEncoderPort;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class BcryptPasswordAdapter implements PasswordEncoderPort {

  private final BCryptPasswordEncoder passwordEncoder =
      new BCryptPasswordEncoder(BcryptPasswordPolicy.BCRYPT_STRENGTH);

  @Override
  public String encode(String rawPassword) {
    if (rawPassword == null) {
      throw new IllegalArgumentException("rawPassword cannot be null");
    }
    BcryptPasswordPolicy.requireWithinLimit(rawPassword);
    return passwordEncoder.encode(rawPassword);
  }

  @Override
  public boolean matches(String rawPassword, String encodedPassword) {
    if (rawPassword == null || encodedPassword == null || !BcryptPasswordPolicy.isWithinLimit(rawPassword)) {
      return false;
    }
    return passwordEncoder.matches(rawPassword, encodedPassword);
  }
}
