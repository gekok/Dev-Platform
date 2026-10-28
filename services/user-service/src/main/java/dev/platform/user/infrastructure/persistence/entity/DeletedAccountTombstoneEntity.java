package dev.platform.user.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@NoArgsConstructor
@Entity
@Table(name = "deleted_account_tombstones")
public class DeletedAccountTombstoneEntity {

    @Id
    @Column(name = "account_id", nullable = false, columnDefinition = "uuid")
    private UUID accountId;

    @Column(name = "deleted_at", nullable = false, columnDefinition = "timestamptz")
    private OffsetDateTime deletedAt;

    public DeletedAccountTombstoneEntity(UUID accountId, OffsetDateTime deletedAt) {
        this.accountId = accountId;
        this.deletedAt = deletedAt;
    }
}
