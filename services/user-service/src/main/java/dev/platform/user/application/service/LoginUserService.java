package dev.platform.user.application.service;

import dev.platform.user.application.command.LoginCommand;
import dev.platform.user.application.port.in.LoginUserPort;
import dev.platform.user.application.port.out.PasswordEncoderPort;
import dev.platform.user.application.port.out.SaveUserPort;
import dev.platform.user.application.port.out.TokenPort;
import dev.platform.user.domain.aggregate.UserAggregate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LoginUserService implements LoginUserPort {

    private final SaveUserPort users;
    private final PasswordEncoderPort passwordEncoder;
    private final TokenPort tokenPort;

    @Override
    public String login(LoginCommand command) {
        final String email = command.email().trim().toLowerCase();
        final UserAggregate user = users.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Email hoac password khong dung"));
        if (!passwordEncoder.matches(command.password(), user.getPasswordHash())) {
            throw new IllegalArgumentException("Email hoac password khong dung");
        }
        return tokenPort.issue(user.getId(), user.getEmail());
    }
}
