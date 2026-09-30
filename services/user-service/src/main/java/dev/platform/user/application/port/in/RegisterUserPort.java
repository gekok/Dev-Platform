package dev.platform.user.application.port.in;

import dev.platform.user.application.command.RegisterCommand;
import dev.platform.user.domain.aggregate.UserAggregate;

// Cổng vào: đăng ký user mới.
public interface RegisterUserPort {
    UserAggregate register(RegisterCommand cmd);
}
