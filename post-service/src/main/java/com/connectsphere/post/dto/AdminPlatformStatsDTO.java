package com.connectsphere.post.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminPlatformStatsDTO {

    private long totalUsers;
    private long dailyActiveUsers;
    private long totalPosts;
    private List<TrendingHashtagDTO> trendingHashtags;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TrendingHashtagDTO {
        private String tag;
        private Integer postCount;
    }
}
