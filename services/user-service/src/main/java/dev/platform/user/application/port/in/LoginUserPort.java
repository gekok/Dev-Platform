package dev.platform.user.application.port.in;

import dev.platform.user.application.command.LoginCommand;

public interface LoginUserPort {
  String login(LoginCommand cmd);
}
