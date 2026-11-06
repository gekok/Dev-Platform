package dev.platform.user.application.service;

import dev.platform.user.application.command.LoginCommand;
import dev.platform.user.application.port.in.LoginUserPort;
import dev.platform.user.application.port.out.PasswordEncoderPort;
import dev.platform.user.application.port.out.SaveUserPort;
import dev.platform.user.application.port.out.TokenPort;
import dev.platform.user.domain.AccountStatus;
import dev.platform.user.domain.aggregate.UserAggregate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Service xử lý đăng nhập tài khoản và phát hành Access Token:
 * 1. Kiểm tra định dạng đầu vào.
 * 2. Tìm tài khoản theo email (chuẩn hóa chữ thường).
 * 3. So khớp mật khẩu qua BCrypt encoder.
 * 4. Kiểm tra trạng thái tài khoản ACTIVE.
 * 5. Phát hành token JWT thông qua TokenPort.
 */
@Service
@RequiredArgsConstructor
public class LoginUserService implements LoginUserPort {

    private final SaveUserPort users;
    private final PasswordEncoderPort passwordEncoder;
    private final TokenPort tokenPort;

    @Override
    public String login(LoginCommand command) {
        if (command == null || command.email() == null || command.password() == null) {
            throw new IllegalArgumentException("Email and password are required");
        }

        String email = command.email().trim().toLowerCase();

        // Không tiết lộ tài khoản có tồn tại hay không khi sai thông tin đăng nhập
        UserAggregate user = users.findByEmail(email).orElseThrow(
                () -> new IllegalArgumentException("Invalid email or password")
        );

        if (!passwordEncoder.matches(command.password(), user.getPasswordHash())) {
            throw new IllegalArgumentException("Invalid email or password");
        }

        if (user.getStatus() != AccountStatus.ACTIVE) {
            throw new IllegalStateException("Account is not active");
        }

        return tokenPort.issue(user.getId(), user.getEmail());
    }
}
