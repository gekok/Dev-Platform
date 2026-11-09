package dev.platform.user.domain.repository;

import dev.platform.user.domain.aggregate.UserAggregate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface UserRepository {
  UserAggregate save(UserAggregate userAggregate);

  Optional<UserAggregate> findById(UUID id);

  Optional<UserAggregate> findByEmail(String email);

  boolean existsByEmail(String email);

  Optional<UserAggregate> findByIdForUpdate(UUID id);

  List<UserAggregate> searchActiveUsers(String query, int limit);

  List<UserAggregate> findByIdIn(Set<UUID> ids);

  List<UserAggregate> findAll();
}
