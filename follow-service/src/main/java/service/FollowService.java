package com.connectsphere.follow.service;

import com.connectsphere.follow.dto.FollowRequestDTO;
import com.connectsphere.follow.dto.FollowResponseDTO;

import java.util.List;

public interface FollowService {
    FollowResponseDTO followUser(FollowRequestDTO requestDTO, Long followerId);
    void unfollowUser(Long followeeId, Long followerId);
    boolean isFollowing(Long followerId, Long followeeId);
    List<FollowResponseDTO> getFollowers(Long userId);
    List<FollowResponseDTO> getFollowing(Long userId);
    long getFollowerCount(Long userId);
    long getFollowingCount(Long userId);
    List<Long> getMutualFollows(Long userId);
    List<Long> getSuggestions(Long userId);
}
