package dev.platform.gateway.security;

import java.util.Set;
import java.util.UUID;

import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;

import reactor.core.publisher.Mono;
import tools.jackson.databind.ObjectMapper;

@Component
public class RedisAccountSnapshotReader {

    private static final String KEY_PREFIX = "auth:account:";

    private final ReactiveStringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public RedisAccountSnapshotReader(ReactiveStringRedisTemplate redis, ObjectMapper objectMapper) {
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    public Mono<AccountSnapshot> read(UUID accountId) {
        String key = KEY_PREFIX + accountId;
        return redis.opsForValue().get(key)
                .switchIfEmpty(Mono.error(new IllegalStateException("Account authorization snapshot is missing")))
                .map(snapshotJson -> parseAndValidate(accountId, snapshotJson))
                .onErrorMap(error -> new IllegalStateException(
                        "Account authorization snapshot could not be verified", error));
    }

    private AccountSnapshot parseAndValidate(UUID expectedAccountId, String snapshotJson) {
        try {
            AccountSnapshot snapshot = objectMapper.readValue(snapshotJson, AccountSnapshot.class);
            if (snapshot == null
                    || !expectedAccountId.equals(snapshot.accountId())
                    || snapshot.status() == null || snapshot.status().isBlank()
                    || snapshot.systemRoles() == null
                    || snapshot.systemRoles().stream().anyMatch(role -> role == null || role.isBlank())
                    || snapshot.authVersion() < 1) {
                throw new IllegalArgumentException("Account authorization snapshot is incomplete or mismatched");
            }
            return snapshot;
        } catch (Exception exception) {
            throw new IllegalStateException("Account authorization snapshot is malformed", exception);
        }
    }

    public record AccountSnapshot(UUID accountId, String status, Set<String> systemRoles, long authVersion) {
    }
}
