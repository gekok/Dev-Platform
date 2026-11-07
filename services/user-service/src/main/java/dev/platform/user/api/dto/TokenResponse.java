package dev.platform.user.api.dto;

public record TokenResponse(String accessToken, String tokenType) {
  public static TokenResponse of(String accessToken) {
    return new TokenResponse(accessToken, "Bearer");
  }
}
