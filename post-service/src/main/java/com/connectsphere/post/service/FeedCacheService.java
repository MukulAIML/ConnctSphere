package com.connectsphere.post.service;

import com.connectsphere.post.dto.PostResponseDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collections;
import java.util.List;

/**
 * Feed Cache Service.
 *
 * <p>Stores and retrieves pre-computed personalized feeds from Redis.
 * Key format: {@code feed:<userId>}
 *
 * <p>Designed to serve 50,000 concurrent users within the 1.5-second SLA.
 */
@Service
public class FeedCacheService {

    private static final Logger logger = LoggerFactory.getLogger(FeedCacheService.class);

    /** Redis key prefix for feed entries */
    public static final String FEED_KEY_PREFIX = "feed:";

    private final RedisTemplate<String, Object> redisTemplate;
    private final long defaultTtlMinutes;

    public FeedCacheService(RedisTemplate<String, Object> redisTemplate,
                            @Value("${feed.cache.ttl-minutes:5}") long defaultTtlMinutes) {
        this.redisTemplate = redisTemplate;
        this.defaultTtlMinutes = defaultTtlMinutes > 0 ? defaultTtlMinutes : 5L;
    }

    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Retrieves the cached feed for a user.
     *
     * @param userId the user whose feed to retrieve
     * @return cached list of posts, or {@code null} if not cached
     */
    @SuppressWarnings("unchecked")
    public List<PostResponseDTO> getCachedFeed(Long userId) {
        try {
            Object value = redisTemplate.opsForValue().get(feedKey(userId));
            if (value instanceof List<?> list) {
                logger.debug("[FeedCache] HIT  for userId={}", userId);
                return (List<PostResponseDTO>) list;
            }
        } catch (Exception e) {
            logger.warn("[FeedCache] Redis GET failed for userId={}: {}", userId, e.getMessage());
        }
        logger.debug("[FeedCache] MISS for userId={}", userId);
        return null;
    }

    /**
     * Stores the computed feed for a user in Redis with a TTL.
     *
     * @param userId the user whose feed to cache
     * @param feed   the pre-computed list of posts
     */
    public void cacheFeed(Long userId, List<PostResponseDTO> feed) {
        cacheFeed(userId, feed, defaultTtlMinutes);
    }

    /**
     * Stores the computed feed for a user in Redis with a custom TTL (minutes).
     */
    public void cacheFeed(Long userId, List<PostResponseDTO> feed, long ttlMinutes) {
        long effectiveTtlMinutes = ttlMinutes > 0 ? ttlMinutes : defaultTtlMinutes;
        try {
            redisTemplate.opsForValue().set(
                    feedKey(userId),
                    feed,
                    Duration.ofMinutes(effectiveTtlMinutes)
            );
            logger.debug("[FeedCache] SET  for userId={} ({} posts, TTL={}m)", userId, feed.size(), effectiveTtlMinutes);
        } catch (Exception e) {
            logger.warn("[FeedCache] Redis SET failed for userId={}: {}", userId, e.getMessage());
        }
    }

    /**
     * Evicts the cached feed for a specific user (e.g. after a new post is created).
     */
    public void evictFeed(Long userId) {
        try {
            redisTemplate.delete(feedKey(userId));
            logger.debug("[FeedCache] EVICT for userId={}", userId);
        } catch (Exception e) {
            logger.warn("[FeedCache] Redis DELETE failed for userId={}: {}", userId, e.getMessage());
        }
    }

    /**
     * Evicts feeds for all users in the given list.
     */
    public void evictFeeds(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) return;
        userIds.forEach(this::evictFeed);
    }

    // ──────────────────────────────────────────────────────────────────────────

    private String feedKey(Long userId) {
        return FEED_KEY_PREFIX + userId;
    }
}
