package dev.platform.user.infrastructure.security;

import dev.platform.user.application.port.out.PasswordEncoderPort;
import org.springframework.stereotype.Component;

import java.util.Objects;

// Mã hoá mật khẩu (stub: trả raw, kỳ 1 thay bằng BCryptPasswordEncoder).
@Component
public class BcryptPasswordAdapter implements PasswordEncoderPort {

    @Override
    public String encode(String raw) {
        // TODO kỳ 1: dùng BCryptPasswordEncoder thay vì trả raw.
        return raw;
    }

    @Override
    public boolean matches(String raw, String hash) {
        // TODO kỳ 1: so bằng BCryptPasswordEncoder.matches.
        return Objects.equals(raw, hash);
    }
}
