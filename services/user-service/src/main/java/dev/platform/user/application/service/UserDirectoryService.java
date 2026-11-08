package dev.platform.user.application.service;

import dev.platform.user.application.dto.UserSummaryDto;
import dev.platform.user.application.port.in.UserDirectoryPort;
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
    return searchUsers(query, 20);
  }

  @Override
  @Transactional(readOnly = true)
  public List<UserSummaryDto> searchUsers(String query, int limit) {
    return userRepository.searchActiveUsers(query, limit).stream()
        .filter(user -> !user.getRoles().contains("SYSTEM_ADMIN"))
        .map(UserSummaryDto::from)
        .toList();
  }

  @Override
  @Transactional(readOnly = true)
  public List<UserSummaryDto> getProfiles(Set<UUID> ids) {
    if (ids == null || ids.isEmpty()) {
      return Collections.emptyList();
    }
    return userRepository.findByIdIn(ids).stream().map(UserSummaryDto::from).toList();
  }
}
