package dev.platform.user.application.command;


import java.util.Set;

public record AdminCreateAccountCommand(
    String email, String rawPassword, String fullName, Set<String> roles) {}
