package dev.platform.user.application.service;

import dev.platform.user.application.command.RegisterCommand;
import dev.platform.user.application.port.in.RegisterUserPort;
import dev.platform.user.application.port.out.PasswordEncoderPort;
import dev.platform.user.application.port.out.SaveUserPort;
import dev.platform.user.domain.aggregate.UserAggregate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RegisterUserService implements RegisterUserPort {

    private final SaveUserPort users;
    private final PasswordEncoderPort passwordEncoder;

    @Override
    @Transactional
    public UserAggregate register(RegisterCommand command) {
        final String email = command.email().trim().toLowerCase();
        if (users.findByEmail(email).isPresent()) {
            throw new IllegalArgumentException("Email da ton tai");
        }
        if (command.password() == null || command.password().length() < 6) {
            throw new IllegalArgumentException("Password toi thieu 6 ky tu");
        }
        return users.save(UserAggregate.create(
                email,
                passwordEncoder.encode(command.password()),
                command.fullName().trim()));
    }
}
