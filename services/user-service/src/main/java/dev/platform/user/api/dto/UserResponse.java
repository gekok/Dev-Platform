package dev.platform.user.api.dto;

import dev.platform.user.domain.aggregate.UserAggregate;

import java.util.UUID;

// DTO hồ sơ user, không bao giờ chứa password.
public record UserResponse(UUID id, String email, String fullName) {

    // Map từ aggregate sang response.
    public static UserResponse from(UserAggregate a) {
        return new UserResponse(a.getId(), a.getEmail(), a.getFullName());
    }
}
