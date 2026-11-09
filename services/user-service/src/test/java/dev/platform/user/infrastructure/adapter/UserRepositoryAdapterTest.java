package dev.platform.user.infrastructure.adapter;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.platform.user.domain.aggregate.UserAggregate;
import dev.platform.user.domain.exception.EmailAlreadyExistsException;
import dev.platform.user.infrastructure.persistence.entity.UserEntity;
import dev.platform.user.infrastructure.persistence.repository.SystemRoleJpaRepository;
import dev.platform.user.infrastructure.persistence.repository.UserJpaRepository;
import dev.platform.user.infrastructure.persistence.repository.UserSystemRoleJpaRepository;
import java.util.Set;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class UserRepositoryAdapterTest {

  @Mock
  private SystemRoleJpaRepository systemRoleJpaRepository;

  @Mock
  private UserJpaRepository userJpaRepository;

  @Mock
  private UserSystemRoleJpaRepository userSystemRoleJpaRepository;

  private UserRepositoryAdapter adapter;

  @BeforeEach
  void setUp() {
    adapter = new UserRepositoryAdapter(
        userJpaRepository, systemRoleJpaRepository, userSystemRoleJpaRepository);
  }

  @Test
  @DisplayName("Chuyển đúng email unique constraint thành EmailAlreadyExistsException")
  void shouldTranslateEmailConstraintViolation() {
    DataIntegrityViolationException failure = integrityFailure("uq_users_email");
    when(userJpaRepository.saveAndFlush(any(UserEntity.class))).thenThrow(failure);

    assertThatThrownBy(() -> adapter.save(newAccount()))
        .isInstanceOf(EmailAlreadyExistsException.class);
    verify(userJpaRepository).saveAndFlush(any(UserEntity.class));
  }

  @Test
  @DisplayName("Giữ nguyên lỗi integrity thuộc constraint khác email")
  void shouldRethrowUnrelatedConstraintViolation() {
    DataIntegrityViolationException failure = integrityFailure("uq_other_constraint");
    when(userJpaRepository.saveAndFlush(any(UserEntity.class))).thenThrow(failure);

    assertThatThrownBy(() -> adapter.save(newAccount()))
        .isSameAs(failure);
  }

  private static UserAggregate newAccount() {
    return UserAggregate.create("new@demo.local", "bcrypt-hash", "New User", Set.of("USER"));
  }

  private static DataIntegrityViolationException integrityFailure(String constraint) {
    return new DataIntegrityViolationException(
        "Database constraint failed",
        new ConstraintViolationException("Database constraint failed", null, constraint));
  }
}
