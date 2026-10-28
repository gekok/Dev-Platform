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

    private UserAggregate(UUID id, String email, String passwordHash, String fullName, AccountStatus status) {
        this.id = id;
        this.email = email;
        this.passwordHash = passwordHash;
        this.fullName = fullName;
        this.status = status;
    }

    // Tạo mới: validate cơ bản trước khi cấp id.
    public static UserAggregate create(String email, String passwordHash, String fullName) {
        if (email == null || !email.contains("@")) {
            throw new IllegalArgumentException("Email khong hop le");
        }
        if (passwordHash == null) {
            throw new IllegalArgumentException("Password hash khong duoc null");
        }
        return new UserAggregate(UUID.randomUUID(), email, passwordHash, fullName, AccountStatus.ACTIVE);
    }

    // Dựng lại từ DB: dùng cho adapter, không validate lại.
    public static UserAggregate rehydrate(UUID id, String email, String passwordHash, String fullName) {
        return rehydrate(id, email, passwordHash, fullName, AccountStatus.ACTIVE);
    }

    public static UserAggregate rehydrate(
            UUID id,
            String email,
            String passwordHash,
            String fullName,
            AccountStatus status) {
        return new UserAggregate(id, email, passwordHash, fullName, status);
    }

    public void markDeleting() {
        if (status != AccountStatus.ACTIVE) {
            throw new IllegalStateException("Only an active account can enter deletion");
        }
        status = AccountStatus.DELETING;
    }
}
