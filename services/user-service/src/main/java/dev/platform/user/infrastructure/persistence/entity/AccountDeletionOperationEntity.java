package dev.platform.user.infrastructure.persistence.entity;

import dev.platform.user.application.deletion.AccountDeletionOperationStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "account_deletion_operations", indexes = {
        @Index(name = "idx_account_deletion_account", columnList = "account_id, created_at")
})
public class AccountDeletionOperationEntity {

    @Id
    @Column(name = "operation_id", nullable = false)
    private UUID operationId;

    @Column(name = "account_id", nullable = false, columnDefinition = "uuid")
    private UUID accountId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AccountDeletionOperationStatus status;

    @Column(name = "created_at", nullable = false, columnDefinition = "timestamptz")
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false, columnDefinition = "timestamptz")
    private OffsetDateTime updatedAt;

    public AccountDeletionOperationEntity(UUID operationId, UUID accountId, OffsetDateTime now) {
        this.operationId = operationId;
        this.accountId = accountId;
        this.status = AccountDeletionOperationStatus.PENDING;
        this.createdAt = now;
        this.updatedAt = now;
    }
}
