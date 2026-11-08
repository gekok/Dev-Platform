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
  public UserSummaryDto createAccount(AdminCreateAccountCommand command) {
    Objects.requireNonNull(command, "command cannot be null");
    Objects.requireNonNull(command.email(), "email cannot be null");
    Objects.requireNonNull(command.rawPassword(), "rawPassword cannot be null");
    Objects.requireNonNull(command.fullName(), "fullName cannot be null");

    String normalizedEmail = command.email().trim().toLowerCase(Locale.ROOT);
    if (userRepository.existsByEmail(normalizedEmail)) {
      throw new EmailAlreadyExistsException("Email already exists");
    }
    String passwordHash = passwordEncoder.encode(command.rawPassword());
    UserAggregate user =
        UserAggregate.create(
            normalizedEmail, passwordHash, command.fullName(), Collections.singleton("USER"));

    UserAggregate saved = userRepository.save(user);
    accountSnapshotSyncPort.recordStatusChange(
        saved.getId(), saved.getStatus().name(), saved.getRoles(), saved.getAuthVersion());
    return UserSummaryDto.from(saved);
  }
}
