package dev.platform.user.domain.aggregate;

import dev.platform.user.domain.AccountStatus;
import lombok.Getter;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

// Cụm gốc User: POJO thuần, không phụ thuộc JPA.
@Getter
public class UserAggregate {

    private final UUID id;
    private final String email;
    private final String passwordHash;
    private final String fullName;
    private AccountStatus status;
    private long authVersion;
    private Set<String> systemRoles;

    private UserAggregate(
            UUID id,
            String email,
            String passwordHash,
            String fullName,
            AccountStatus status,
            long authVersion,
            Set<String> systemRoles) {
        this.id = id;
        this.email = email;
        this.passwordHash = passwordHash;
        this.fullName = fullName;
        this.status = status;
        this.authVersion = authVersion;
        this.systemRoles = systemRoles != null && !systemRoles.isEmpty()
                ? Collections.unmodifiableSet(new LinkedHashSet<>(systemRoles))
                : Collections.singleton("USER");
    }

    // Tạo mới: validate cơ bản trước khi cấp id, mặc định role USER.
    public static UserAggregate create(String email, String passwordHash, String fullName) {
        if (email == null || !email.contains("@")) {
            throw new IllegalArgumentException("Email khong hop le");
        }
        if (passwordHash == null) {
            throw new IllegalArgumentException("Password hash khong duoc null");
        }
        return new UserAggregate(
                UUID.randomUUID(), email, passwordHash, fullName, AccountStatus.ACTIVE, 1L, Set.of("USER"));
    }

    // Dựng lại từ DB: dùng cho adapter, không validate lại.
    public static UserAggregate rehydrate(UUID id, String email, String passwordHash, String fullName) {
        return rehydrate(id, email, passwordHash, fullName, AccountStatus.ACTIVE, 1L, Set.of("USER"));
    }

    public static UserAggregate rehydrate(
            UUID id,
            String email,
            String passwordHash,
            String fullName,
            AccountStatus status) {
        return rehydrate(id, email, passwordHash, fullName, status, 1L, Set.of("USER"));
    }

    public static UserAggregate rehydrate(
            UUID id,
            String email,
            String passwordHash,
            String fullName,
            AccountStatus status,
            long authVersion) {
        return rehydrate(id, email, passwordHash, fullName, status, authVersion, Set.of("USER"));
    }

    public static UserAggregate rehydrate(
            UUID id,
            String email,
            String passwordHash,
            String fullName,
            AccountStatus status,
            long authVersion,
            Set<String> systemRoles) {
        return new UserAggregate(id, email, passwordHash, fullName, status, authVersion, systemRoles);
    }

    public boolean changeStatus(AccountStatus newStatus) {
        if (newStatus == null) {
            throw new IllegalArgumentException("status khong duoc null");
        }
        if (status == newStatus) {
            return false;
        }
        status = newStatus;
        authVersion++;
        return true;
    }

    public boolean grantRole(String role) {
        if (role == null || role.isBlank()) {
            throw new IllegalArgumentException("role khong duoc de trong");
        }
        Set<String> newRoles = new LinkedHashSet<>(systemRoles);
        if (newRoles.add(role.toUpperCase())) {
            this.systemRoles = Collections.unmodifiableSet(newRoles);
            authVersion++;
            return true;
        }
        return false;
    }

    public boolean revokeRole(String role) {
        if (role == null || role.isBlank()) {
            throw new IllegalArgumentException("role khong duoc de trong");
        }
        Set<String> newRoles = new LinkedHashSet<>(systemRoles);
        if (newRoles.remove(role.toUpperCase())) {
            this.systemRoles = Collections.unmodifiableSet(newRoles);
            authVersion++;
            return true;
        }
        return false;
    }
}
