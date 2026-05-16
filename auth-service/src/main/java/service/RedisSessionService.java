package com.connectsphere.auth.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * RedisSessionService — manages authenticated JWT sessions in Redis.
 *
 * <p>Two use-cases:
 * <ol>
 *   <li><b>Session cache</b> — on login/register we write a
 *       {@code session:{userId}} key so other services can verify
 *       a user is "known-active" without hitting the database.</li>
 *   <li><b>Token blocklist</b> — on logout we write a
 *       {@code blocklist:{jti|token-hash}} key so the JWT filter
 *       can reject invalidated tokens before they expire.</li>
 * </ol>
 *
 * <p>If Redis is unavailable the service degrades gracefully —
 * all operations catch {@link Exception} and log a warning so that
 * the rest of the auth flow continues uninterrupted.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RedisSessionService {

    private final StringRedisTemplate redisTemplate;

    @Value("${app.redis.session-ttl-seconds:86400}")
    private long sessionTtlSeconds;

    // ── Key helpers ───────────────────────────────────────

    private String sessionKey(Long userId) {
        return "session:" + userId;
    }

    private String blocklistKey(String tokenHash) {
        return "blocklist:" + tokenHash;
    }

    // ── Session Cache ─────────────────────────────────────

    /**
     * Store an active session for {@code userId}.
     * The value is the raw JWT; TTL matches the token expiry.
     */
    public void cacheSession(Long userId, String token) {
        try {
            redisTemplate.opsForValue().set(
                    sessionKey(userId),
                    token,
                    Duration.ofSeconds(sessionTtlSeconds));
            log.debug("Cached session for userId={}", userId);
        } catch (Exception e) {
            log.warn("Redis unavailable — session NOT cached for userId={}: {}", userId, e.getMessage());
        }
    }

    /**
     * Retrieve the cached JWT for {@code userId}, or {@code null} if
     * the session has expired or Redis is unavailable.
     */
    public String getCachedSession(Long userId) {
        try {
            return redisTemplate.opsForValue().get(sessionKey(userId));
        } catch (Exception e) {
            log.warn("Redis unavailable — cannot fetch session for userId={}: {}", userId, e.getMessage());
            return null;
        }
    }

    /**
     * Remove the session cache entry for {@code userId} (called on logout
     * or account suspension/deletion).
     */
    public void evictSession(Long userId) {
        try {
            redisTemplate.delete(sessionKey(userId));
            log.debug("Evicted session for userId={}", userId);
        } catch (Exception e) {
            log.warn("Redis unavailable — session NOT evicted for userId={}: {}", userId, e.getMessage());
        }
    }

    // ── Token Blocklist ───────────────────────────────────

    /**
     * Add a token to the blocklist so it cannot be reused after logout.
     * The blocklist entry expires after {@code ttlSeconds}.
     *
     * @param tokenIdentifier a unique token identifier (JTI or token hash)
     * @param ttlSeconds      remaining lifetime of the token
     */
    public void blockToken(String tokenIdentifier, long ttlSeconds) {
        try {
            redisTemplate.opsForValue().set(
                    blocklistKey(tokenIdentifier),
                    "blocked",
                    Duration.ofSeconds(Math.max(ttlSeconds, 1)));
            log.debug("Token added to blocklist: {}", tokenIdentifier);
        } catch (Exception e) {
            log.warn("Redis unavailable — token NOT blocklisted: {}", e.getMessage());
        }
    }

    /**
     * Returns {@code true} if the token identifier is on the blocklist.
     */
    public boolean isTokenBlocked(String tokenIdentifier) {
        try {
            return Boolean.TRUE.equals(
                    redisTemplate.hasKey(blocklistKey(tokenIdentifier)));
        } catch (Exception e) {
            log.warn("Redis unavailable — cannot check blocklist: {}", e.getMessage());
            return false;  // fail-open: let JWT signature/expiry guard the request
        }
    }
}
