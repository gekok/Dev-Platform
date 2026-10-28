package dev.platform.user.infrastructure.adapter;

import dev.platform.user.application.deletion.AccountDeletionOperationStatus;
import dev.platform.user.application.deletion.AccountDeletionOperationView;
import dev.platform.user.application.port.out.AccountDeletionStorePort;
import dev.platform.user.domain.aggregate.UserAggregate;
import dev.platform.user.infrastructure.persistence.entity.AccountDeletionOperationEntity;
import dev.platform.user.infrastructure.persistence.entity.DeletedAccountTombstoneEntity;
import dev.platform.user.infrastructure.persistence.repository.AccountDeletionOperationJpaRepository;
import dev.platform.user.infrastructure.persistence.repository.DeletedAccountTombstoneJpaRepository;
import dev.platform.user.infrastructure.persistence.repository.UserJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class AccountDeletionPersistenceAdapter implements AccountDeletionStorePort {

    private final UserJpaRepository userRepository;
    private final AccountDeletionOperationJpaRepository operationRepository;
    private final DeletedAccountTombstoneJpaRepository tombstoneRepository;

    @Override
    public AccountDeletionOperationView start(UUID accountId, UUID operationId) {
        var user = userRepository.findByIdForUpdate(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found: " + accountId));
        UserAggregate account = UserAggregate.rehydrate(
                user.getId(), user.getEmail(), user.getPasswordHash(), user.getFullName(), user.getStatus());
        account.markDeleting();

        OffsetDateTime now = OffsetDateTime.now();
        user.setStatus(account.getStatus());
        userRepository.save(user);
        AccountDeletionOperationEntity operation = operationRepository.save(
                new AccountDeletionOperationEntity(operationId, accountId, now));
        return toView(operation);
    }

    @Override
    public Optional<AccountDeletionOperationView> find(UUID operationId) {
        return operationRepository.findById(operationId).map(this::toView);
    }

    @Override
    public void complete(UUID operationId, UUID accountId) {
        AccountDeletionOperationEntity operation = operationRepository.findById(operationId)
                .orElseThrow(() -> new IllegalArgumentException("Deletion operation not found: " + operationId));
        if (!operation.getAccountId().equals(accountId)) {
            throw new IllegalArgumentException("Account does not match deletion operation " + operationId);
        }
        if (operation.getStatus() == AccountDeletionOperationStatus.COMPLETED) {
            return;
        }

        OffsetDateTime now = OffsetDateTime.now();
        if (!tombstoneRepository.existsById(accountId)) {
            tombstoneRepository.save(new DeletedAccountTombstoneEntity(accountId, now));
        }
        userRepository.deleteById(accountId);
        operation.setStatus(AccountDeletionOperationStatus.COMPLETED);
        operation.setUpdatedAt(now);
        operationRepository.save(operation);
    }

    private AccountDeletionOperationView toView(AccountDeletionOperationEntity operation) {
        return new AccountDeletionOperationView(
                operation.getOperationId(),
                operation.getAccountId(),
                operation.getStatus(),
                operation.getCreatedAt(),
                operation.getUpdatedAt());
    }
}
