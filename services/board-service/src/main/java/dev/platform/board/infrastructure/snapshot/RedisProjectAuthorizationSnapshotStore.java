package dev.platform.board.infrastructure.snapshot;

import java.util.List;
import java.util.UUID;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class RedisProjectAuthorizationSnapshotStore {

    public enum ApplyResult {
        APPLIED,
        ALREADY_CURRENT,
        SUPERSEDED
    }

    private static final DefaultRedisScript<Long> APPLY_IF_NEWER = new DefaultRedisScript<>("""
            local incoming = cjson.decode(ARGV[1])
            local currentRaw = redis.call('GET', KEYS[1])
            if currentRaw then
              local current = cjson.decode(currentRaw)
              local currentVersion = tonumber(current.authorizationVersion)
              local incomingVersion = tonumber(incoming.authorizationVersion)
              if currentVersion > incomingVersion then
                return -1
              end
              if currentVersion == incomingVersion then
                return 0
              end
            end
            redis.call('SET', KEYS[1], ARGV[1])
            return 1
            """, Long.class);

    private final StringRedisTemplate redis;

    public ApplyResult apply(Long projectId, UUID userId, String snapshotJson) {
        Long result = redis.execute(APPLY_IF_NEWER, List.of(key(projectId, userId)), snapshotJson);
        if (result == null) {
            throw new IllegalStateException("Redis membership snapshot compare-and-set returned no result");
        }
        if (result == 1L) {
            return ApplyResult.APPLIED;
        }
        if (result == 0L) {
            return ApplyResult.ALREADY_CURRENT;
        }
        if (result == -1L) {
            return ApplyResult.SUPERSEDED;
        }
        throw new IllegalStateException("Redis membership snapshot compare-and-set returned an unknown result");
    }

    public static String key(Long projectId, UUID userId) {
        return "auth:project:" + projectId + ":member:" + userId;
    }
}
