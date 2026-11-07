package dev.platform.user.application.port.out;

import java.util.UUID;

/**
 * Cổng ra cấp phát và xác thực Token.
 */
public interface TokenPort {

    /**
     * Cấp phát Access Token JWT cho người dùng.
     */
    String issue(UUID userId, String email);

    /**
     * Xác thực Access Token và trích xuất UUID của người dùng từ subject.
     *
     * @param token Chuỗi JWT access token
     * @return UUID của người dùng đã được xác thực
     * @throws dev.platform.user.domain.exception.AuthenticationException nếu token không hợp lệ hoặc hết hạn
     */
    UUID parseAndValidate(String token);
}
