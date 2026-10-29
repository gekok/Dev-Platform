package dev.platform.user.infrastructure.snapshot;

import java.util.List;
import java.util.UUID;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class RedisAccountAuthorizationSnapshotStore {

    private static final DefaultRedisScript<Long> APPLY_IF_NEWER = new DefaultRedisScript<>("""
            local incoming = cjson.decode(ARGV[1])
            local currentRaw = redis.call('GET', KEYS[1])
            if currentRaw then
              local current = cjson.decode(currentRaw)
              if tonumber(current.authVersion) >= tonumber(incoming.authVersion) then
                return 0
              end
            end
            redis.call('SET', KEYS[1], ARGV[1])
            return 1
            """, Long.class);

    private final StringRedisTemplate redis;

    public boolean apply(UUID accountId, String snapshotJson) {
        Long result = redis.execute(APPLY_IF_NEWER, List.of(key(accountId)), snapshotJson);
        return result != null && result == 1L;
    }

    public static String key(UUID accountId) {
        return "auth:account:" + accountId;
    }
}
