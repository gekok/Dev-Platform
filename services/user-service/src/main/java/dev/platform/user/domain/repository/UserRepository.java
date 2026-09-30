package dev.platform.user.domain.repository;

import dev.platform.user.domain.aggregate.UserAggregate;

import java.util.Optional;
import java.util.UUID;

// Hợp đồng kho User ở tầng domain.
public interface UserRepository {
    UserAggregate save(UserAggregate a);

    Optional<UserAggregate> findById(UUID id);

    Optional<UserAggregate> findByEmail(String email);
}
