package dev.platform.user.api.dto;

// DTO token đăng nhập.
public record TokenResponse(String accessToken, String tokenType) {

    // Mặc định tokenType là Bearer.
    public static TokenResponse of(String accessToken) {
        return new TokenResponse(accessToken, "Bearer");
    }
}
