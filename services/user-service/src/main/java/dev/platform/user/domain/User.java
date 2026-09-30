package dev.platform.user.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * Gốc cụm User (aggregate root).
 * Giữ tài khoản đăng nhập. Các service khác chỉ giữ userId dạng UUID,
 * không tạo quan hệ JPA sang bảng này vì khác database.
 *
 * @deprecated Kỳ 1 thay bằng UserAggregate (domain thuần) + UserEntity
 *             (infrastructure). Hiện giữ để không vỡ compile.
 */
@Deprecated
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "full_name", length = 255)
    private String fullName;
}
