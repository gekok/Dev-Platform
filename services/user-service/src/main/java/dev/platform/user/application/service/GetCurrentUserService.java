package dev.platform.user.application.service;

import dev.platform.user.application.port.in.GetCurrentUserPort;
import dev.platform.user.application.port.out.SaveUserPort;
import dev.platform.user.domain.aggregate.UserAggregate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Service xử lý truy vấn hồ sơ của tài khoản hiện tại từ danh tính đã xác thực:
 * 1. Kiểm tra định dạng đầu vào userId.
 * 2. Tìm tài khoản trong cơ sở dữ liệu theo UUID.
 * 3. Kiểm tra trạng thái tài khoản bắt buộc phải là ACTIVE.
 * 4. Trả về thông tin UserAggregate.
 */
@Service
@RequiredArgsConstructor
public class GetCurrentUserService implements GetCurrentUserPort {

    private final SaveUserPort users;
    private final AccountAccessGuard accountAccessGuard;

    @Override
    public UserAggregate me(UUID userId) {
        if (userId == null) {
            throw new IllegalArgumentException("userId cannot be null");
        }

        UserAggregate user = users.findById(userId)
                .orElseThrow(() -> new NoSuchElementException("User not found: " + userId));

        accountAccessGuard.requireActive(user);

        return user;
    }
}
