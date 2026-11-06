package dev.platform.user.domain.repository;

import dev.platform.user.domain.aggregate.UserAggregate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository {
  UserAggregate save(UserAggregate user);
  Optional<UserAggregate> findById(UUID id);
  Optional<UserAggregate> findByEmail(String email);
  Optional<UserAggregate> findByIdForUpdate(UUID id);
  boolean existsByEmail(String email);
  List<UserAggregate> findAll();
}
