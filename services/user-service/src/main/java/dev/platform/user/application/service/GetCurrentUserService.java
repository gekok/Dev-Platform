package dev.platform.user.application.service;

import dev.platform.user.application.port.in.GetCurrentUserPort;
import dev.platform.user.application.port.out.SaveUserPort;
import dev.platform.user.domain.aggregate.UserAggregate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GetCurrentUserService implements GetCurrentUserPort {

    private final SaveUserPort users;

    @Override
    public UserAggregate me(UUID userId) {
        return users.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Khong tim thay user"));
    }
}
