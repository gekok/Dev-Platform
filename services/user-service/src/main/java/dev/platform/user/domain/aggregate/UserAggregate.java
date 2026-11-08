package dev.platform.user.domain.aggregate;

import dev.platform.user.domain.AccountStatus;
import java.util.*;
import lombok.Getter;

@Getter
public class UserAggregate {
  private final UUID id;
  private final String email;
  private final String passwordHash;
  private final String fullName;
  private AccountStatus status;
  private long authVersion;
  private Set<String> roles;

  public UserAggregate(
      UUID id,
      String email,
      String passwordHash,
      String fullName,
      AccountStatus status,
      long authVersion,
      Set<String> roles) {
    this.id = id;
    this.email = email;
    this.passwordHash = passwordHash;
    this.fullName = fullName;
    this.status = status;
    this.authVersion = authVersion;
    this.roles =
        (roles == null || roles.isEmpty())
            ? Collections.singleton("USER")
            : Collections.unmodifiableSet(new LinkedHashSet<>(roles));
  }

  public static UserAggregate create(
      String email, String passwordHash, String fullName, Set<String> roles) {
    Objects.requireNonNull(email, "email cannot be null");
    Objects.requireNonNull(passwordHash, "passwordHash cannot be null");
    Objects.requireNonNull(fullName, "fullName cannot be null");
    return new UserAggregate(
        UUID.randomUUID(),
        email.trim().toLowerCase(),
        passwordHash,
        fullName,
        AccountStatus.ACTIVE,
        1L,
        roles);
  }

  public static UserAggregate rehydrate(
      UUID id,
      String email,
      String passwordHash,
      String fullName,
      AccountStatus status,
      long authVersion,
      Set<String> roles) {
    return new UserAggregate(id, email, passwordHash, fullName, status, authVersion, roles);
  }

  public boolean changeStatus(AccountStatus newStatus) {
    Objects.requireNonNull(newStatus, "newStatus cannot be null");
    if (this.status == newStatus) {
      return false;
    }
    this.status = newStatus;
    authVersion++;
    return true;
  }

  public boolean grantRole(String role) {
    Objects.requireNonNull(roles, "roles cannot be null");
    String normalizedRole = validateAndNormalizeRoles(role);
    if (this.roles.contains(normalizedRole)) {
      return false;
    }
    Set<String> newRoles = new LinkedHashSet<>(this.roles);
    newRoles.add(normalizedRole);
    this.roles = Collections.unmodifiableSet(newRoles);
    this.authVersion++;
    return true;
  }

  public boolean revokeRole(String role) {
    Objects.requireNonNull(role, "role cannot be null");
    String normalizedRole = validateAndNormalizeRoles(role);
    if (!this.roles.contains(normalizedRole)) {
      return false;
    }
    if (this.roles.size() == 1) {
      throw new IllegalArgumentException("cannot revoke roles because there is more than one role");
    }
    Set<String> update = new LinkedHashSet<>(this.roles);
    update.remove(normalizedRole);
    this.roles = Collections.unmodifiableSet(update);
    this.authVersion++;
    return true;
  }

  public boolean updateRoles(Set<String> roles) {
    Objects.requireNonNull(roles, "roles cannot be null");
    if (roles.isEmpty()) {
      throw new IllegalArgumentException("roles cannot be empty");
    }
    Set<String> newRoles = new LinkedHashSet<>();
    for (String role : roles) {
      newRoles.add(validateAndNormalizeRoles(role));
    }
    this.roles = Collections.unmodifiableSet(newRoles);
    this.authVersion++;
    return true;
  }

  private String validateAndNormalizeRoles(String role) {
    if (role == null || role.isBlank()) {
      throw new IllegalArgumentException("role cannot be null or blank");
    }
    String normalizedRole = role.trim().toUpperCase(Locale.ROOT);
    if (normalizedRole.length() < 3 || normalizedRole.length() > 40) {
      throw new IllegalArgumentException("role need to be between 3 and 40 characters");
    }
    return normalizedRole;
  }
}
