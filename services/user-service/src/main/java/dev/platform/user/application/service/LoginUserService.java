package dev.platform.user.application.service;

import dev.platform.user.application.command.LoginCommand;
import dev.platform.user.application.port.in.LoginUserPort;
import dev.platform.user.application.port.out.PasswordEncoderPort;
import dev.platform.user.application.port.out.SaveUserPort;
import dev.platform.user.application.port.out.TokenPort;
import dev.platform.user.domain.AccountStatus;
import dev.platform.user.domain.aggregate.UserAggregate;
import dev.platform.user.domain.exception.AuthenticationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Service xử lý đăng nhập tài khoản và phát hành Access Token:
 * 1. Kiểm tra định dạng đầu vào.
 * 2. Tìm tài khoản theo email (chuẩn hóa chữ thường).
 * 3. So khớp mật khẩu qua BCrypt encoder (có bảo vệ chống timing attack).
 * 4. Kiểm tra trạng thái tài khoản ACTIVE.
 * 5. Phát hành token JWT thông qua TokenPort.
 */
@Service
@RequiredArgsConstructor
public class LoginUserService implements LoginUserPort {

    // Chuỗi hash BCrypt chuẩn dùng để thực thi kiểm tra giả, chống tấn công timing side-channel khi email không tồn tại
    private static final String DUMMY_BCRYPT_HASH =
            "$2a$10$7EqJtq98hPqEX7fNZaODoOFn9730D95A/e0Z3W1A2C3E4G5H6I7J8";

    private final SaveUserPort users;
    private final PasswordEncoderPort passwordEncoder;
    private final TokenPort tokenPort;

    @Override
    public String login(LoginCommand command) {
        if (command == null || command.email() == null || command.password() == null) {
            throw new IllegalArgumentException("Email and password are required");
        }

        String email = command.email().trim().toLowerCase();

        UserAggregate user = users.findByEmail(email).orElse(null);

        // Chống timing attack (Side-Channel User Enumeration):
        // Dù tài khoản không tồn tại, hệ thống vẫn chạy kiểm tra BCrypt với dummy hash
        // để thời gian phản hồi của 2 nhánh (sai email vs sai mật khẩu) là tương đương nhau.
        if (user == null) {
            passwordEncoder.matches(command.password(), DUMMY_BCRYPT_HASH);
            throw new AuthenticationException("Invalid email or password");
        }

        if (!passwordEncoder.matches(command.password(), user.getPasswordHash())) {
            throw new AuthenticationException("Invalid email or password");
        }

        if (user.getStatus() != AccountStatus.ACTIVE) {
            throw new IllegalStateException("Account is not active");
        }

        return tokenPort.issue(user.getId(), user.getEmail());
    }
}
