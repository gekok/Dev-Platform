package dev.platform.user.infrastructure.security;

import dev.platform.user.application.port.out.PasswordEncoderPort;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class BcryptPasswordAdapter implements PasswordEncoderPort {

  private final BCryptPasswordEncoder delegate = new BCryptPasswordEncoder(12);

  @Override
  public String encode(String rawPassword) {
    if (rawPassword == null) {
      throw new IllegalArgumentException("rawPassword cannot be null");
    }
    return delegate.encode(rawPassword);
  }

  @Override
  public boolean matches(String rawPassword, String encodedPassword) {
    if (rawPassword == null || encodedPassword == null) {
      return false;
    }
    return delegate.matches(rawPassword, encodedPassword);
  }
}
