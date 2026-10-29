package dev.platform.user.infrastructure.adapter;

import dev.platform.user.application.port.out.SaveUserPort;
import dev.platform.user.domain.aggregate.UserAggregate;
import dev.platform.user.infrastructure.persistence.entity.UserEntity;
import dev.platform.user.infrastructure.persistence.repository.UserJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

// Adapter ghi/đọc user qua JPA, map Aggregate <-> Entity.
@Component
@RequiredArgsConstructor
public class SaveUserAdapter implements SaveUserPort {

    private final UserJpaRepository jpa;

    @Override
    public UserAggregate save(UserAggregate a) {
        UserEntity saved = jpa.save(toEntity(a));
        return toAggregate(saved);
    }

    @Override
    public Optional<UserAggregate> findById(UUID id) {
        return jpa.findById(id).map(this::toAggregate);
    }

    @Override
    public Optional<UserAggregate> findByEmail(String email) {
        return jpa.findByEmail(email).map(this::toAggregate);
    }

    // Map tối thiểu đủ biên dịch: giữ đủ 4 field.
    private UserEntity toEntity(UserAggregate a) {
        return UserEntity.builder()
                .id(a.getId())
                .email(a.getEmail())
                .passwordHash(a.getPasswordHash())
                .fullName(a.getFullName())
                .status(a.getStatus())
                .authVersion(a.getAuthVersion())
                .build();
    }

    private UserAggregate toAggregate(UserEntity e) {
        return UserAggregate.rehydrate(
                e.getId(), e.getEmail(), e.getPasswordHash(), e.getFullName(), e.getStatus(), e.getAuthVersion());
    }
}
