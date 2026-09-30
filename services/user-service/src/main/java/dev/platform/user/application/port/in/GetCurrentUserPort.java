package dev.platform.user.application.port.in;

import dev.platform.user.domain.aggregate.UserAggregate;

import java.util.UUID;

// Cổng vào: lấy hồ sơ của chính user.
public interface GetCurrentUserPort {
    UserAggregate me(UUID userId);
}
