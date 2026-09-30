package dev.platform.user.application.command;

// Lệnh đăng nhập: dữ liệu vào cho use-case login.
public record LoginCommand(String email, String password) {
}
