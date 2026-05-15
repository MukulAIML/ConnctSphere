package com.connectsphere.post.scheduler;

import com.connectsphere.post.dto.PostResponseDTO;
import com.connectsphere.post.entity.Post;
import com.connectsphere.post.repository.PostRepository;
import com.connectsphere.post.service.FeedCacheService;
import com.connectsphere.post.service.PostService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Feed Pre-Computation Scheduler.
 *
 * <p>Periodically queries the follow-service for all active users and
 * pre-computes their personalized feed, storing results in Redis.
 * This background job ensures that the {@code GET /posts/feed/{userId}}
 * endpoint can respond in < 1.5 s even under 50,000 concurrent users.
 *
 * <p>Schedule: every 5 minutes (configurable via {@code feed.scheduler.cron}).
 */
@Component
public class FeedPreComputationJob {

    private static final Logger logger = LoggerFactory.getLogger(FeedPreComputationJob.class);

    private final PostService postService;
    private final PostRepository postRepository;
    private final FeedCacheService feedCacheService;
    private final RestTemplate restTemplate;

    @Value("${follow-service.url}")
    private String followServiceUrl;

    @Value("${feed.cache.ttl-minutes:5}")
    private long cacheTtlMinutes;

    public FeedPreComputationJob(PostService postService,
                                 PostRepository postRepository,
                                 FeedCacheService feedCacheService,
                                 RestTemplate restTemplate) {
        this.postService = postService;
        this.postRepository = postRepository;
        this.feedCacheService = feedCacheService;
        this.restTemplate = restTemplate;
    }

    /**
     * Main scheduled method. Cron is configured in application.yml.
     * Default: every 5 minutes.
     */
    @Scheduled(cron = "${feed.scheduler.cron:0 */5 * * * *}")
    public void preComputeFeeds() {
        logger.info("[FeedJob] Starting feed pre-computation");
        long start = System.currentTimeMillis();

        Set<Long> activeUserIds = resolveActiveUserIds();
        if (activeUserIds.isEmpty()) {
            logger.warn("[FeedJob] No active users found — skipping");
            return;
        }

        int success = 0;
        int skipped = 0;

        for (Long userId : activeUserIds) {
            try {
                List<PostResponseDTO> feed = postService.getFeedByUserId(userId);
                feedCacheService.cacheFeed(userId, feed, cacheTtlMinutes);
                success++;
            } catch (Exception e) {
                logger.warn("[FeedJob] Failed to compute feed for userId={}: {}", userId, e.getMessage());
                skipped++;
            }
        }

        long elapsed = System.currentTimeMillis() - start;
        logger.info("[FeedJob] Completed. success={}, skipped={}, elapsed={}ms",
                success, skipped, elapsed);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Resolve which user IDs should have their feed pre-computed.
    // Strategy: collect distinct authorIds from posts (they are active users)
    // and optionally augment from the follow-service.
    // ──────────────────────────────────────────────────────────────────────────

    private Set<Long> resolveActiveUserIds() {
        // Start with all users who have posted recently (always available locally)
        Set<Long> userIds = postRepository.findAll()
                .stream()
                .filter(p -> !p.getIsDeleted())
                .map(Post::getAuthorId)
                .collect(Collectors.toSet());

        // Augment with followers from follow-service (best-effort)
        try {
            String url = followServiceUrl + "/follows/all-users";
            ResponseEntity<List> response =
                    restTemplate.exchange(url, HttpMethod.GET, null, List.class);

            if (response.getBody() != null) {
                for (Object obj : response.getBody()) {
                    try {
                        if (obj instanceof Map<?, ?> map) {
                            Object id = map.get("userId");
                            if (id == null) id = map.get("id");
                            if (id != null) userIds.add(Long.valueOf(id.toString()));
                        } else if (obj instanceof Number num) {
                            userIds.add(num.longValue());
                        }
                    } catch (Exception ignored) { /* skip malformed entries */ }
                }
            }
        } catch (Exception e) {
            logger.warn("[FeedJob] Could not reach follow-service for user list — using local set only");
        }

        logger.info("[FeedJob] Pre-computing feeds for {} users", userIds.size());
        return userIds;
    }
}
