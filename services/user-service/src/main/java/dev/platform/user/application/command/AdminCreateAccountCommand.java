package dev.platform.user.application.command;

public record AdminCreateAccountCommand(
    String email,
    String rawPassword,
    String fullName
) {
}
