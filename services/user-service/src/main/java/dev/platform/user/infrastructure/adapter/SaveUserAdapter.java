package dev.platform.user.infrastructure.adapter;

import dev.platform.user.application.port.out.SaveUserPort;
import dev.platform.user.domain.aggregate.UserAggregate;
import dev.platform.user.infrastructure.persistence.entity.SystemRoleEntity;
import dev.platform.user.infrastructure.persistence.entity.UserEntity;
import dev.platform.user.infrastructure.persistence.entity.UserSystemRoleEntity;
import dev.platform.user.infrastructure.persistence.entity.UserSystemRoleId;
import dev.platform.user.infrastructure.persistence.repository.SystemRoleJpaRepository;
import dev.platform.user.infrastructure.persistence.repository.UserJpaRepository;
import dev.platform.user.infrastructure.persistence.repository.UserSystemRoleJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

// Adapter ghi/đọc user qua JPA, map Aggregate <-> Entity kèm System Roles.
@Component
@RequiredArgsConstructor
public class SaveUserAdapter implements SaveUserPort {

    private final UserJpaRepository jpa;
    private final UserSystemRoleJpaRepository userSystemRoleJpa;
    private final SystemRoleJpaRepository systemRoleJpa;

    @Override
    public UserAggregate save(UserAggregate a) {
        UserEntity saved = jpa.save(toEntity(a));
        syncRoles(saved, a.getSystemRoles());
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
        Set<String> roles = Set.of("USER");
        if (userSystemRoleJpa != null) {
            List<UserSystemRoleEntity> userRoles = userSystemRoleJpa.findByIdUserId(e.getId());
            if (userRoles != null && !userRoles.isEmpty()) {
                roles = userRoles.stream()
                        .map(r -> r.getId().getRoleCode())
                        .collect(Collectors.toSet());
            }
        }
        return UserAggregate.rehydrate(
                e.getId(), e.getEmail(), e.getPasswordHash(), e.getFullName(), e.getStatus(), e.getAuthVersion(), roles);
    }

    private void syncRoles(UserEntity user, Set<String> roles) {
        if (userSystemRoleJpa == null || systemRoleJpa == null) {
            return;
        }
        Set<String> targetRoles = (roles == null || roles.isEmpty()) ? Set.of("USER") : roles;
        List<UserSystemRoleEntity> current = userSystemRoleJpa.findByIdUserId(user.getId());
        Set<String> currentCodes = current.stream()
                .map(r -> r.getId().getRoleCode())
                .collect(Collectors.toSet());

        for (String r : targetRoles) {
            if (!currentCodes.contains(r)) {
                SystemRoleEntity roleEntity = systemRoleJpa.findById(r)
                        .orElseGet(() -> systemRoleJpa.save(new SystemRoleEntity(r, r + " role")));
                UserSystemRoleEntity userRole = new UserSystemRoleEntity();
                userRole.setId(new UserSystemRoleId(user.getId(), r));
                userRole.setUser(user);
                userRole.setRole(roleEntity);
                userRole.setGrantedAt(OffsetDateTime.now());
                userSystemRoleJpa.save(userRole);
            }
        }

        for (UserSystemRoleEntity c : current) {
            if (!targetRoles.contains(c.getId().getRoleCode())) {
                userSystemRoleJpa.delete(c);
            }
        }
    }
}
