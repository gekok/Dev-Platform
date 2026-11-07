package dev.platform.user.infrastructure.adapter;

import dev.platform.user.domain.AccountStatus;
import dev.platform.user.domain.aggregate.UserAggregate;
import dev.platform.user.domain.repository.UserRepository;
import dev.platform.user.infrastructure.persistence.entity.SystemRoleEntity;
import dev.platform.user.infrastructure.persistence.entity.UserEntity;
import dev.platform.user.infrastructure.persistence.entity.UserSystemRoleEntity;
import dev.platform.user.infrastructure.persistence.entity.UserSystemRoleId;
import dev.platform.user.infrastructure.persistence.repository.SystemRoleJpaRepository;
import dev.platform.user.infrastructure.persistence.repository.UserJpaRepository;
import dev.platform.user.infrastructure.persistence.repository.UserSystemRoleJpaRepository;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class UserRepositoryAdapter implements UserRepository {
  private final SystemRoleJpaRepository systemRoleJpaRepository;
  private final UserJpaRepository userJpaRepository;
  private final UserSystemRoleJpaRepository userSystemRoleJpaRepository;

  @Override
  @Transactional
  public UserAggregate save(UserAggregate user) {
    UserEntity userEntity = toEntity(user);
    UserEntity savedUser = userJpaRepository.save(userEntity);
    syncRoles(savedUser, user.getRoles());
    return toAggregate(savedUser);
  }

  private void syncRoles(UserEntity user, Set<String> targetRoles) {
    Set<String> rolesToSync =
        (targetRoles == null || targetRoles.isEmpty())
            ? Collections.singleton("USER")
            : targetRoles;

    List<UserSystemRoleEntity> currentEntities =
        userSystemRoleJpaRepository.findByIdUserId(user.getId());

    Set<String> currentCodes =
        currentEntities.stream().map(e -> e.getId().getRoleCode()).collect(Collectors.toSet());

    // 1. Nếu có role mới trong danh sách mà DB chưa có -> Thêm mới vào DB
    for (String code : rolesToSync) {
      if (!currentCodes.contains(code)) {
        SystemRoleEntity systemRole =
            systemRoleJpaRepository
                .findById(code)
                .orElseGet(
                    () -> systemRoleJpaRepository.save(new SystemRoleEntity(code, code + " role")));

        UserSystemRoleEntity newRole = new UserSystemRoleEntity();
        newRole.setId(new UserSystemRoleId(user.getId(), code));
        newRole.setUser(user);
        newRole.setRole(systemRole);
        newRole.setGrantedAt(OffsetDateTime.now());
        userSystemRoleJpaRepository.save(newRole);
      }
    }

    // 2. Nếu trong DB đang có role mà danh sách mới không còn -> Xóa khỏi DB
    for (UserSystemRoleEntity current : currentEntities) {
      if (!rolesToSync.contains(current.getId().getRoleCode())) {
        userSystemRoleJpaRepository.delete(current);
      }
    }
  }

  @Override
  public Optional<UserAggregate> findById(UUID id) {
    return userJpaRepository.findById(id).map(this::toAggregate);
  }

  @Override
  public Optional<UserAggregate> findByEmail(String email) {
    return userJpaRepository.findByEmail(email).map(this::toAggregate);
  }

  @Override
  public Optional<UserAggregate> findByIdForUpdate(UUID id) {
    return userJpaRepository.findByIdForUpdate(id).map(this::toAggregate);
  }

  @Override
  public boolean existsByEmail(String email) {
    return userJpaRepository.existsByEmail(email);
  }

  @Override
  public List<UserAggregate> findAll() {
    return toAggregatesBatch(userJpaRepository.findAll());
  }

  @Override
  public List<UserAggregate> searchActiveUsers(String query, int limit) {
    int effectiveLimit = (limit <= 0) ? 20 : Math.min(limit, 100);
    Pageable pageable = PageRequest.of(0, effectiveLimit);
    String q = (query == null) ? "" : query.trim();
    List<UserEntity> entities = userJpaRepository.searchActiveUsers(AccountStatus.ACTIVE, q, pageable);
    return toAggregatesBatch(entities);
  }

  @Override
  public List<UserAggregate> findByIdIn(Set<UUID> ids) {
    if (ids == null || ids.isEmpty()) {
      return Collections.emptyList();
    }
    List<UserEntity> entities = userJpaRepository.findByIdIn(ids);
    return toAggregatesBatch(entities);
  }

  private List<UserAggregate> toAggregatesBatch(List<UserEntity> entities) {
    if (entities == null || entities.isEmpty()) {
      return Collections.emptyList();
    }
    List<UUID> userIds = entities.stream().map(UserEntity::getId).toList();
    List<UserSystemRoleEntity> roleEntities = userSystemRoleJpaRepository.findByIdUserIdIn(userIds);

    Map<UUID, Set<String>> rolesMap = new HashMap<>();
    for (UserSystemRoleEntity re : roleEntities) {
      rolesMap.computeIfAbsent(re.getId().getUserId(), k -> new LinkedHashSet<>()).add(re.getId().getRoleCode());
    }

    return entities.stream()
        .map(e -> UserAggregate.rehydrate(
            e.getId(),
            e.getEmail(),
            e.getPasswordHash(),
            e.getFullName(),
            e.getStatus(),
            e.getAuthVersion(),
            rolesMap.getOrDefault(e.getId(), Collections.singleton("USER"))))
        .toList();
  }

  private UserEntity toEntity(UserAggregate aggregate) {
    return UserEntity.builder()
        .id(aggregate.getId())
        .email(aggregate.getEmail())
        .passwordHash(aggregate.getPasswordHash())
        .fullName(aggregate.getFullName())
        .status(aggregate.getStatus())
        .authVersion(aggregate.getAuthVersion())
        .build();
  }

  private UserAggregate toAggregate(UserEntity entity) {
    List<UserSystemRoleEntity> userRoleEntities =
        userSystemRoleJpaRepository.findByIdUserId(entity.getId());

    Set<String> roleCodes =
        userRoleEntities.stream()
            .map(roleEntity -> roleEntity.getId().getRoleCode())
            .collect(Collectors.toCollection(LinkedHashSet::new));

    return UserAggregate.rehydrate(
        entity.getId(),
        entity.getEmail(),
        entity.getPasswordHash(),
        entity.getFullName(),
        entity.getStatus(),
        entity.getAuthVersion(),
        roleCodes);
  }
}
