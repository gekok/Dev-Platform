package dev.platform.user.application.service;

import dev.platform.user.application.command.AdminCreateAccountCommand;
import dev.platform.user.application.dto.UserSummaryDto;
import dev.platform.user.application.port.in.AdminCreateAccountPort;
import dev.platform.user.application.port.out.AccountSnapshotSyncPort;
import dev.platform.user.application.port.out.PasswordEncoderPort;
import dev.platform.user.domain.aggregate.UserAggregate;
import dev.platform.user.domain.exception.EmailAlreadyExistsException;
import dev.platform.user.domain.repository.UserRepository;
import java.util.Collections;
import java.util.Locale;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminCreateAccountService implements AdminCreateAccountPort {
  private final UserRepository userRepository;
  private final PasswordEncoderPort passwordEncoder;
  private final AccountSnapshotSyncPort accountSnapshotSyncPort;

  @Override
  @Transactional
  public UserSummaryDto createAccount(AdminCreateAccountCommand cmd) {
    Objects.requireNonNull(cmd, "command cannot be null");
    Objects.requireNonNull(cmd.email(), "email cannot be null");
    Objects.requireNonNull(cmd.rawPassword(), "rawPassword cannot be null");
    Objects.requireNonNull(cmd.fullName(), "fullName cannot be null");

    String normalizedEmail = cmd.email().trim().toLowerCase(Locale.ROOT);
    if (userRepository.existsByEmail(normalizedEmail)) {
      throw new EmailAlreadyExistsException("Email already exists: " + normalizedEmail);
    }
    String passwordHash = passwordEncoder.encode(cmd.rawPassword());

    // Role mặc định luôn là USER, không nhận từ payload tạo account để chống tự cấp SYSTEM_ADMIN
    UserAggregate newUser =
        UserAggregate.create(normalizedEmail, passwordHash, cmd.fullName(), Collections.singleton("USER"));

    UserAggregate saved = userRepository.save(newUser);
    accountSnapshotSyncPort.recordStatusChange(
        saved.getId(), saved.getStatus().name(), saved.getRoles(), saved.getAuthVersion());
    return UserSummaryDto.from(saved);
  }
}
