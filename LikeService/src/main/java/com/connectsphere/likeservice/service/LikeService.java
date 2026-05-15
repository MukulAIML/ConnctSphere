package com.connectsphere.likeservice.service;

import java.util.List;
import java.util.Optional;

import com.connectsphere.likeservice.dto.ReactionSummaryResponse;
import com.connectsphere.likeservice.entity.Like;

public interface LikeService {
	Like likeTarget(int userId, int targetId, String targetType, String reactionType);

	void unlikeTarget(int userId, int targetId, String targetType);

	boolean hasLiked(int userId, int targetId, String targetType);

	List<Like> getLikesByUser(int userId);

	List<Like> getLikesByTarget(int targetId, String targetType);

	long getLikeCount(int targetId, String targetType);

	long getLikeCountByType(int targetId, String targetType, String reactionType);

	ReactionSummaryResponse getReactionSummary(int targetId, String targetType);

	Like changeReaction(int userId, int targetId, String targetType, String newReactionType);

	Optional<Like> getUserReaction(int userId, int targetId, String targetType);
}
