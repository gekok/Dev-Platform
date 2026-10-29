package dev.platform.task.infrastructure.adapter;

import java.io.IOException;
import java.util.Set;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.platform.task.application.port.out.AuthorizationSnapshotReader;
import dev.platform.task.application.service.AuthorizationSnapshotUnavailableException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RedisAuthorizationSnapshotAdapter implements AuthorizationSnapshotReader {

    private record AccountSnapshotValue(UUID accountId, String status, Set<String> systemRoles, long authVersion) {
    }

    private record ProjectMembershipSnapshotValue(
            Long projectId, UUID userId, String status, String role,
            long membershipVersion, long authorizationVersion) {
    }

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    @Override
    public AccountSnapshot getAccount(UUID userId) {
        String key = "auth:account:" + userId;
        String json = read(key);
        try {
            AccountSnapshotValue snapshot = objectMapper.readValue(json, AccountSnapshotValue.class);
            if (snapshot == null
                    || !userId.equals(snapshot.accountId())
                    || snapshot.status() == null || snapshot.status().isBlank()
                    || snapshot.systemRoles() == null
                    || snapshot.systemRoles().stream().anyMatch(role -> role == null || role.isBlank())
                    || snapshot.authVersion() < 1) {
                throw new AuthorizationSnapshotUnavailableException(
                        "Account authorization snapshot is incomplete or does not match its key");
            }
            return new AccountSnapshot(
                    snapshot.accountId(), snapshot.status(), snapshot.systemRoles(), snapshot.authVersion());
        } catch (IOException exception) {
            throw new AuthorizationSnapshotUnavailableException("Invalid Account authorization snapshot", exception);
        }
    }

    @Override
    public ProjectMembershipSnapshot getProjectMembership(Long projectId, UUID userId) {
        String key = "auth:project:" + projectId + ":member:" + userId;
        String json = read(key);
        try {
            ProjectMembershipSnapshotValue snapshot = objectMapper.readValue(json, ProjectMembershipSnapshotValue.class);
            if (snapshot == null
                    || !projectId.equals(snapshot.projectId())
                    || !userId.equals(snapshot.userId())
                    || snapshot.status() == null || snapshot.status().isBlank()
                    || ("ACTIVE".equals(snapshot.status()) && (snapshot.role() == null || snapshot.role().isBlank()))
                    || snapshot.membershipVersion() < 1
                    || snapshot.authorizationVersion() < 1) {
                throw new AuthorizationSnapshotUnavailableException(
                        "Project membership snapshot is incomplete or does not match its key");
            }
            return new ProjectMembershipSnapshot(snapshot.projectId(), snapshot.userId(), snapshot.status(),
                    snapshot.role(), snapshot.membershipVersion(), snapshot.authorizationVersion());
        } catch (IOException exception) {
            throw new AuthorizationSnapshotUnavailableException("Invalid Project membership snapshot", exception);
        }
    }

    private String read(String key) {
        try {
            String json = redis.opsForValue().get(key);
            if (json == null) {
                throw new AuthorizationSnapshotUnavailableException("Authorization snapshot is missing: " + key);
            }
            return json;
        } catch (AuthorizationSnapshotUnavailableException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new AuthorizationSnapshotUnavailableException("Could not read authorization snapshot: " + key,
                    exception);
        }
    }
}
