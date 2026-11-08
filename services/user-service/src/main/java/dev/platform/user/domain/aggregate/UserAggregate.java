package dev.platform.user.domain.aggregate;

import dev.platform.user.domain.AccountStatus;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
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

    private UserAggregate(UUID id, String email, String passwordHash, String fullName, AccountStatus status, long authVersion, Set<String> roles) {
        this.id = id;
        this.email = email;
        this.passwordHash = passwordHash;
        this.fullName = fullName;
        this.status = status;
        this.authVersion = authVersion;
        this.roles = (roles == null || roles.isEmpty())
                ? Collections.singleton("USER")
                : Collections.unmodifiableSet(new LinkedHashSet<>(roles));
    }

    public static UserAggregate create(String email, String passwordHash, String fullName, Set<String> roles) {
        Objects.requireNonNull(email, "email cannot be null");
        Objects.requireNonNull(passwordHash, "passwordHash cannot be null");
        Objects.requireNonNull(fullName, "fullName cannot be null");
        return new UserAggregate(
                UUID.randomUUID(),
                email.trim().toLowerCase(Locale.ROOT),
                passwordHash,
                fullName.trim(),
                AccountStatus.ACTIVE,
                1L,
                roles
        );
    }

    public static UserAggregate rehydrate(UUID id, String email, String passwordHash, String fullName, AccountStatus status, long authVersion, Set<String> roles) {
        return new UserAggregate(id, email, passwordHash, fullName, status, authVersion, roles);
    }

    public boolean changeStatus(AccountStatus newStatus) {
        Objects.requireNonNull(newStatus, "newStatus cannot be null");
        if (this.status == newStatus) {
            return false;
        }
        this.status = newStatus;
        this.authVersion++;
        return true;
    }

    public boolean grantRole(String role) {
        Objects.requireNonNull(role, "role cannot be null");
        String normalizedRole = validateAndNormalizeRole(role);
        if (this.roles.contains(normalizedRole)) {
            return false;
        }
        Set<String> updated = new LinkedHashSet<>(this.roles);
        updated.add(normalizedRole);
        this.roles = Collections.unmodifiableSet(updated);
        this.authVersion++;
        return true;
    }

    public boolean revokeRole(String role) {
        Objects.requireNonNull(role, "role cannot be null");
        String normalizedRole = validateAndNormalizeRole(role);
        if (!this.roles.contains(normalizedRole)) {
            return false;
        }
        if (this.roles.size() <= 1) {
            throw new IllegalArgumentException("cannot revoke the last remaining role");
        }
        Set<String> updated = new LinkedHashSet<>(this.roles);
        updated.remove(normalizedRole);
        this.roles = Collections.unmodifiableSet(updated);
        this.authVersion++;
        return true;
    }

    public boolean updateRoles(Set<String> newRoles) {
        Objects.requireNonNull(newRoles, "newRoles cannot be null");
        if (newRoles.isEmpty()) {
            throw new IllegalArgumentException("newRoles cannot be empty");
        }
        Set<String> normalizedRoles = new LinkedHashSet<>();
        for (String r : newRoles) {
            normalizedRoles.add(validateAndNormalizeRole(r));
        }
        if (this.roles.equals(normalizedRoles)) {
            return false;
        }
        this.roles = Collections.unmodifiableSet(normalizedRoles);
        this.authVersion++;
        return true;
    }

    private String validateAndNormalizeRole(String role) {
        if (role == null || role.isBlank()) {
            throw new IllegalArgumentException("role cannot be null or empty");
        }
        String normalizedRole = role.trim().toUpperCase(Locale.ROOT);
        if (normalizedRole.length() < 3 || normalizedRole.length() > 40) {
            throw new IllegalArgumentException("role needs to be between 3 and 40 characters");
        }
        return normalizedRole;
    }
}
