package com.connectsphere.like.service;

import com.connectsphere.like.dto.LikeRequestDTO;
import com.connectsphere.like.dto.LikeResponseDTO;
import com.connectsphere.like.entity.ReactionType;
import com.connectsphere.like.entity.TargetType;

import java.util.List;
import java.util.Map;

public interface LikeService {
    LikeResponseDTO likeTarget(LikeRequestDTO requestDTO, Long userId);
    void unlikeTarget(Long targetId, TargetType targetType, Long userId);
    LikeResponseDTO changeReaction(LikeRequestDTO requestDTO, Long userId);
    boolean hasUserLikedTarget(Long userId, Long targetId, TargetType targetType);
    List<LikeResponseDTO> getLikesByTarget(Long targetId, TargetType targetType);
    List<LikeResponseDTO> getLikesByUser(Long userId);
    long getLikesCountForTarget(Long targetId, TargetType targetType);
    long getLikeCountByType(Long targetId, TargetType targetType, ReactionType reactionType);
    Map<String, Long> getReactionSummary(Long targetId, TargetType targetType);
}
