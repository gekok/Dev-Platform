package dev.platform.user.application.port.out;

// Cổng ra: mã hoá mật khẩu, tách khỏi thư viện cụ thể.
public interface PasswordEncoderPort {
    String encode(String raw);

    boolean matches(String raw, String hash);
}
