package dev.platform.user.infrastructure.security;

import dev.platform.user.application.port.out.PasswordEncoderPort;
import dev.platform.user.domain.exception.DomainException;
import org.springframework.stereotype.Component;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import java.util.Objects;

@Component
public class BcryptPasswordAdapter implements PasswordEncoderPort {

    private final BCryptPasswordEncoder passwordEncoder= new BCryptPasswordEncoder();
    @Override
    public String encode(String raw) {
        if (raw == null) {
            throw new DomainException("Password cannot be null");
        }
        return passwordEncoder.encode(raw);
    }

    @Override
    public boolean matches(String raw, String hash) {
        if (raw == null || hash == null) {
            return false;
        }
        return passwordEncoder.matches(raw, hash);
    }
}
