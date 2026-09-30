package dev.platform.user.application.command;

// Lệnh đăng ký: dữ liệu vào cho use-case register.
public record RegisterCommand(String email, String password, String fullName) {
}
