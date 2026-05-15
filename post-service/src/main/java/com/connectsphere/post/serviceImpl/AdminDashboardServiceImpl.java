package com.connectsphere.post.serviceImpl;

import com.connectsphere.post.dto.AdminBroadcastRequestDTO;
import com.connectsphere.post.dto.AdminBroadcastResponseDTO;
import com.connectsphere.post.dto.AdminPlatformStatsDTO;
import com.connectsphere.post.exception.BadRequestException;
import com.connectsphere.post.repository.PostRepository;
import com.connectsphere.post.service.AdminDashboardService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class AdminDashboardServiceImpl implements AdminDashboardService {

    private final PostRepository postRepository;
    private final RestTemplate restTemplate;

    @Value("${auth-service.url}")
    private String authServiceUrl;

    @Value("${search-service.url}")
    private String searchServiceUrl;

    @Value("${notification-service.url}")
    private String notificationServiceUrl;

    public AdminDashboardServiceImpl(PostRepository postRepository, RestTemplate restTemplate) {
        this.postRepository = postRepository;
        this.restTemplate = restTemplate;
    }

    @Override
    @Transactional(readOnly = true)
    public AdminPlatformStatsDTO getPlatformStats() {
        UserStats stats = fetchUserStats();
        return AdminPlatformStatsDTO.builder()
                .totalUsers(stats.totalUsers())
                .dailyActiveUsers(stats.dailyActiveUsers())
                .totalPosts(postRepository.countByIsDeletedFalse())
                .trendingHashtags(fetchTrendingHashtags())
                .build();
    }

    @Override
    public AdminBroadcastResponseDTO sendBroadcast(AdminBroadcastRequestDTO request, Long actorId) {
        List<Long> recipients = resolveRecipients(request.getRecipientScope(), request.getRecipientIds());
        if (recipients.isEmpty()) {
            throw new BadRequestException("No recipients available for broadcast");
        }

        String message = Optional.ofNullable(request.getMessage()).map(String::trim).orElse("");
        if (message.isEmpty()) {
            throw new BadRequestException("Broadcast message cannot be empty");
        }

        Map<String, Object> payload = Map.of(
                "recipientIds", recipients,
                "actorId", actorId,
                "type", "BROADCAST",
                "message", message,
                "targetId", actorId,
                "targetType", "USER"
        );

        String url = notificationServiceUrl + "/notifications/bulk";
        restTemplate.exchange(url, HttpMethod.POST, new HttpEntity<>(payload), Object.class);

        return AdminBroadcastResponseDTO.builder()
                .recipientsCount(recipients.size())
                .message("Broadcast dispatched successfully")
                .build();
    }

    private List<Long> resolveRecipients(AdminBroadcastRequestDTO.RecipientScope scope, List<Long> candidateIds) {
        if (scope == AdminBroadcastRequestDTO.RecipientScope.TARGETED) {
            return sanitizeUserIds(candidateIds);
        }
        return fetchAllUserIds();
    }

    private List<Long> fetchAllUserIds() {
        String url = authServiceUrl + "/auth/admin/users";
        try {
            Map<?, ?> body = restTemplate.exchange(url, HttpMethod.GET, HttpEntity.EMPTY, Map.class).getBody();
            Object data = body == null ? null : body.get("data");
            if (!(data instanceof List<?> users)) {
                return List.of();
            }

            LinkedHashSet<Long> ids = new LinkedHashSet<>();
            for (Object entry : users) {
                if (!(entry instanceof Map<?, ?> userMap)) {
                    continue;
                }
                parseLong(userMap.get("userId")).ifPresent(ids::add);
            }
            return new ArrayList<>(ids);
        } catch (Exception ex) {
            throw new BadRequestException("Unable to resolve recipients from auth-service");
        }
    }

    private UserStats fetchUserStats() {
        String url = authServiceUrl + "/auth/admin/stats/users";
        try {
            Map<?, ?> body = restTemplate.exchange(url, HttpMethod.GET, HttpEntity.EMPTY, Map.class).getBody();
            Object data = body == null ? null : body.get("data");
            if (!(data instanceof Map<?, ?> map)) {
                return new UserStats(0L, 0L);
            }

            long totalUsers = parseLong(map.get("totalUsers")).orElse(0L);
            long dailyActiveUsers = parseLong(map.get("dailyActiveUsers")).orElse(0L);
            return new UserStats(totalUsers, dailyActiveUsers);
        } catch (Exception ex) {
            return new UserStats(0L, 0L);
        }
    }

    private List<AdminPlatformStatsDTO.TrendingHashtagDTO> fetchTrendingHashtags() {
        String url = searchServiceUrl + "/search/hashtags/trending";
        try {
            Map<?, ?> body = restTemplate.exchange(url, HttpMethod.GET, HttpEntity.EMPTY, Map.class).getBody();
            Object data = body == null ? null : body.get("data");
            if (!(data instanceof List<?> list)) {
                return List.of();
            }

            List<AdminPlatformStatsDTO.TrendingHashtagDTO> hashtags = new ArrayList<>();
            for (Object entry : list) {
                if (!(entry instanceof Map<?, ?> map)) {
                    continue;
                }
                String tag = map.get("tag") == null ? null : map.get("tag").toString();
                Integer postCount = parseLong(map.get("postCount")).map(Long::intValue).orElse(0);
                if (tag != null && !tag.isBlank()) {
                    hashtags.add(AdminPlatformStatsDTO.TrendingHashtagDTO.builder()
                            .tag(tag)
                            .postCount(postCount)
                            .build());
                }
            }
            return hashtags;
        } catch (Exception ex) {
            return List.of();
        }
    }

    private List<Long> sanitizeUserIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<Long> unique = new LinkedHashSet<>();
        for (Long id : ids) {
            if (id != null && id > 0) {
                unique.add(id);
            }
        }
        return new ArrayList<>(unique);
    }

    private Optional<Long> parseLong(Object value) {
        if (value == null) {
            return Optional.empty();
        }
        if (value instanceof Number number) {
            return Optional.of(number.longValue());
        }
        if (value instanceof String text) {
            try {
                return Optional.of(Long.parseLong(text.trim()));
            } catch (Exception ignored) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    private record UserStats(long totalUsers, long dailyActiveUsers) {
    }
}
