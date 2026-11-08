package dev.platform.user.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import dev.platform.user.domain.AccountStatus;
import dev.platform.user.domain.aggregate.UserAggregate;
import dev.platform.user.domain.exception.AccountNotFoundException;
import dev.platform.user.domain.repository.UserRepository;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GetAccountStatusServiceTest {
  @Mock
  private UserRepository userRepository;

  @Test
  void returnsStatusForExistingNonActiveAccount() {
    UUID userId = UUID.randomUUID();
    UserAggregate account = UserAggregate.rehydrate(
        userId, "user@demo.local", "hash", "Demo User", AccountStatus.SUSPENDED, 2L, Set.of("USER"));
    when(userRepository.findById(userId)).thenReturn(Optional.of(account));

    AccountStatus status = new GetAccountStatusService(userRepository).getAccountStatus(userId);

    assertEquals(AccountStatus.SUSPENDED, status);
  }

  @Test
  void throwsNotFoundWhenAccountDoesNotExist() {
    UUID userId = UUID.randomUUID();
    when(userRepository.findById(userId)).thenReturn(Optional.empty());

    assertThrows(AccountNotFoundException.class,
        () -> new GetAccountStatusService(userRepository).getAccountStatus(userId));
  }
}
