package dev.platform.user.application.port.in;

import dev.platform.user.application.command.LoginCommand;

// Cổng vào: đăng nhập, trả JWT stub (kỳ 1 viết ruột).
public interface LoginUserPort {
    String login(LoginCommand cmd);
}
