package dev.platform.user.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import dev.platform.user.application.dto.UserSummaryDto;
import dev.platform.user.domain.AccountStatus;
import dev.platform.user.domain.aggregate.UserAggregate;
import dev.platform.user.domain.repository.UserRepository;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserDirectoryServiceTest {

  @Mock
  private UserRepository userRepository;

  private UserDirectoryService service;

  @BeforeEach
  void setUp() {
    service = new UserDirectoryService(userRepository);
  }

  @Test
  @DisplayName("Tìm kiếm chỉ trả về các tài khoản ACTIVE khớp từ khóa")
  void shouldSearchOnlyActiveMatchingUsers() {
    UserAggregate active1 = UserAggregate.rehydrate(
        UUID.randomUUID(), "alex@demo.local", "pass", "Alex Morgan", AccountStatus.ACTIVE, 1L, Set.of("USER"));
    UserAggregate active2 = UserAggregate.rehydrate(
        UUID.randomUUID(), "john@demo.local", "pass", "John Doe", AccountStatus.ACTIVE, 1L, Set.of("USER"));
    UserAggregate locked = UserAggregate.rehydrate(
        UUID.randomUUID(), "alex_locked@demo.local", "pass", "Alex Locked", AccountStatus.LOCKED, 1L, Set.of("USER"));

    when(userRepository.findAll()).thenReturn(List.of(active1, active2, locked));

    List<UserSummaryDto> results = service.searchUsers("alex");

    assertThat(results).hasSize(1);
    assertThat(results.get(0).email()).isEqualTo("alex@demo.local");
  }

  @Test
  @DisplayName("Lấy profiles theo danh sách UUIDs")
  void shouldGetProfilesByIds() {
    UUID id1 = UUID.randomUUID();
    UUID id2 = UUID.randomUUID();
    UserAggregate u1 = UserAggregate.rehydrate(
        id1, "u1@demo.local", "pass", "User One", AccountStatus.ACTIVE, 1L, Set.of("USER"));
    UserAggregate u2 = UserAggregate.rehydrate(
        id2, "u2@demo.local", "pass", "User Two", AccountStatus.ACTIVE, 1L, Set.of("USER"));

    when(userRepository.findAll()).thenReturn(List.of(u1, u2));

    List<UserSummaryDto> results = service.getProfiles(Set.of(id1));

    assertThat(results).hasSize(1);
    assertThat(results.get(0).id()).isEqualTo(id1);
  }
}
