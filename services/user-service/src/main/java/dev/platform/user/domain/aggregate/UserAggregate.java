package dev.platform.user.domain.aggregate;

import dev.platform.user.domain.AccountStatus;
import lombok.Getter;

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

    private UserAggregate(UUID id, String email, String passwordHash, String fullName, AccountStatus status,
            long authVersion) {
        this.id = id;
        this.email = email;
        this.passwordHash = passwordHash;
        this.fullName = fullName;
        this.status = status;
        this.authVersion = authVersion;
    }

    // Tạo mới: validate cơ bản trước khi cấp id.
    public static UserAggregate create(String email, String passwordHash, String fullName) {
        if (email == null || !email.contains("@")) {
            throw new IllegalArgumentException("Email khong hop le");
        }
        if (passwordHash == null) {
            throw new IllegalArgumentException("Password hash khong duoc null");
        }
        return new UserAggregate(UUID.randomUUID(), email, passwordHash, fullName, AccountStatus.ACTIVE, 1L);
    }

    // Dựng lại từ DB: dùng cho adapter, không validate lại.
    public static UserAggregate rehydrate(UUID id, String email, String passwordHash, String fullName) {
        return rehydrate(id, email, passwordHash, fullName, AccountStatus.ACTIVE, 1L);
    }

    public static UserAggregate rehydrate(
            UUID id,
            String email,
            String passwordHash,
            String fullName,
            AccountStatus status) {
        return rehydrate(id, email, passwordHash, fullName, status, 1L);
    }

    public static UserAggregate rehydrate(
            UUID id,
            String email,
            String passwordHash,
            String fullName,
            AccountStatus status,
            long authVersion) {
        return new UserAggregate(id, email, passwordHash, fullName, status, authVersion);
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
}
