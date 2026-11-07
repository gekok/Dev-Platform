package dev.platform.user.application.service;

import dev.platform.user.application.dto.UserSummaryDto;
import dev.platform.user.application.port.in.UserDirectoryPort;
import dev.platform.user.domain.AccountStatus;
import dev.platform.user.domain.repository.UserRepository;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserDirectoryService implements UserDirectoryPort {
  private final UserRepository userRepository;

  @Override
  @Transactional(readOnly = true)
  public List<UserSummaryDto> searchUsers(String query) {
    String q = (query == null) ? "" : query.trim().toLowerCase();
    return userRepository.findAll().stream()
        .filter(user -> user.getStatus() == AccountStatus.ACTIVE)
        .filter(user ->
            q.isEmpty()
                || user.getEmail().toLowerCase().contains(q)
                || user.getFullName().toLowerCase().contains(q))
        .map(UserSummaryDto::from)
        .toList();
  }

  @Override
  @Transactional(readOnly = true)
  public List<UserSummaryDto> getProfiles(Set<UUID> ids) {
    if (ids == null || ids.isEmpty()) {
      return Collections.emptyList();
    }
    return userRepository.findAll().stream()
        .filter(user -> ids.contains(user.getId()))
        .map(UserSummaryDto::from)
        .toList();
  }
}
