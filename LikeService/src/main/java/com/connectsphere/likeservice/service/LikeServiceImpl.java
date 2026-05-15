package com.connectsphere.likeservice.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.connectsphere.likeservice.client.CommentServiceClient;
import com.connectsphere.likeservice.client.PostServiceClient;
import com.connectsphere.likeservice.dto.CommentSummaryDTO;
import com.connectsphere.likeservice.dto.CreateNotificationRequest;
import com.connectsphere.likeservice.dto.PostSummaryDTO;
import com.connectsphere.likeservice.dto.ReactionSummaryResponse;
import com.connectsphere.likeservice.entity.Like;
import com.connectsphere.likeservice.exception.ResourceNotFoundException;
import com.connectsphere.likeservice.messaging.NotificationProducer;
import com.connectsphere.likeservice.repository.LikeRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class LikeServiceImpl implements LikeService {

	private final LikeRepository likeRepository;
	private final PostServiceClient postServiceClient;
	private final CommentServiceClient commentServiceClient;
	private final NotificationProducer notificationProducer;

	/**
	 * React to a target (post or comment) with a given reaction type.
	 *
	 * Facebook-style behaviour:
	 *  - If the user has NOT reacted yet → create a new reaction and increment the
	 *    total counter on the owning service.
	 *  - If the user HAS already reacted with the SAME type → no-op (idempotent).
	 *  - If the user HAS already reacted with a DIFFERENT type → update the reaction
	 *    type in-place. The total counter stays the same (still one reaction from
	 *    this user). Only the per-type breakdown changes.
	 */
	@Override
	public Like likeTarget(int userId, int targetId, String targetType, String reactionType) {
		validateUserId(userId);
		validateTargetId(targetId);
		String normalizedTargetType = normalizeTargetType(targetType);
		String normalizedReactionType = normalizeReactionType(reactionType);
		validateReactionType(normalizedReactionType);
		validateTargetType(normalizedTargetType);

		Optional<Like> existing = likeRepository.findByUserIdAndTargetIdAndTargetType(userId, targetId, normalizedTargetType);

		if (existing.isPresent()) {
			Like existingLike = existing.get();
			if (existingLike.getReactionType().equals(normalizedReactionType)) {
				// Exact same reaction — idempotent, nothing to do
				log.debug("Idempotent like: userId={}, targetId={}, type={}", userId, targetId, normalizedReactionType);
				return existingLike;
			}

			// Different reaction type — update in-place, DO NOT touch the counter
			String oldType = existingLike.getReactionType();
			existingLike.setReactionType(normalizedReactionType);
			Like updated = likeRepository.save(existingLike);
			log.info("Reaction changed in-place: userId={}, targetId={}, {} -> {}", userId, targetId, oldType, normalizedReactionType);
			emitLikeNotificationIfNeeded(userId, targetId, normalizedTargetType, normalizedReactionType);
			return updated;
		}

		// Brand-new reaction from this user
		Like newLike = Like.builder()
				.userId(userId)
				.targetId(targetId)
				.targetType(normalizedTargetType)
				.reactionType(normalizedReactionType)
				.build();

		Like saved;
		try {
			saved = likeRepository.save(newLike);
		} catch (DataIntegrityViolationException e) {
			log.warn("Concurrent like detected — loading existing: userId={}, targetId={}", userId, targetId);
			return likeRepository.findByUserIdAndTargetIdAndTargetType(userId, targetId, normalizedTargetType)
					.orElseThrow(() -> new IllegalStateException("Concurrent like inconsistency"));
		}

		// Increment total counter on the owning service (only for new reactions)
		incrementTargetCounter(targetId, normalizedTargetType);
		log.info("Reaction added: likeId={}, userId={}, targetId={}, targetType={}, reaction={}",
				saved.getLikeId(), userId, targetId, normalizedTargetType, normalizedReactionType);
		emitLikeNotificationIfNeeded(userId, targetId, normalizedTargetType, normalizedReactionType);

		return saved;
	}

	@Override
	public void unlikeTarget(int userId, int targetId, String targetType) {
		validateUserId(userId);
		validateTargetId(targetId);
		String normalizedTargetType = normalizeTargetType(targetType);
		validateTargetType(normalizedTargetType);

		Optional<Like> existing = likeRepository.findByUserIdAndTargetIdAndTargetType(userId, targetId, normalizedTargetType);
		if (existing.isEmpty()) {
			log.debug("Unlike no-op: no reaction found for userId={}, targetId={}", userId, targetId);
			return;
		}

		likeRepository.deleteByUserIdAndTargetIdAndTargetType(userId, targetId, normalizedTargetType);
		// Decrement total counter (one fewer user reacted)
		decrementTargetCounter(targetId, normalizedTargetType);
		log.info("Reaction removed: userId={}, targetId={}, targetType={}", userId, targetId, normalizedTargetType);
	}

	@Override
	@Transactional(readOnly = true)
	public boolean hasLiked(int userId, int targetId, String targetType) {
		validateUserId(userId);
		validateTargetId(targetId);
		String normalizedTargetType = normalizeTargetType(targetType);
		validateTargetType(normalizedTargetType);
		return likeRepository.existsByUserIdAndTargetIdAndTargetType(userId, targetId, normalizedTargetType);
	}

	@Override
	@Transactional(readOnly = true)
	public List<Like> getLikesByUser(int userId) {
		validateUserId(userId);
		return likeRepository.findByUserId(userId);
	}

	@Override
	@Transactional(readOnly = true)
	public List<Like> getLikesByTarget(int targetId, String targetType) {
		validateTargetId(targetId);
		String normalizedTargetType = normalizeTargetType(targetType);
		validateTargetType(normalizedTargetType);
		return likeRepository.findByTargetIdAndTargetType(targetId, normalizedTargetType);
	}

	@Override
	@Transactional(readOnly = true)
	public long getLikeCount(int targetId, String targetType) {
		validateTargetId(targetId);
		String normalizedTargetType = normalizeTargetType(targetType);
		validateTargetType(normalizedTargetType);
		return likeRepository.countByTargetIdAndTargetType(targetId, normalizedTargetType);
	}

	@Override
	@Transactional(readOnly = true)
	public long getLikeCountByType(int targetId, String targetType, String reactionType) {
		validateTargetId(targetId);
		String normalizedReactionType = normalizeReactionType(reactionType);
		String normalizedTargetType = normalizeTargetType(targetType);
		validateReactionType(normalizedReactionType);
		validateTargetType(normalizedTargetType);
		return likeRepository.countByTargetIdAndTargetTypeAndReactionType(targetId, normalizedTargetType, normalizedReactionType);
	}

	/**
	 * Returns a Facebook-style reaction summary:
	 *  - totalCount  : total number of unique users who reacted
	 *  - reactions   : map of reactionType -> count, ordered by count descending
	 *  - topReactions: up to 3 reaction types with the highest counts (for icon display)
	 */
	@Override
	@Transactional(readOnly = true)
	public ReactionSummaryResponse getReactionSummary(int targetId, String targetType) {
		validateTargetId(targetId);
		String normalizedTargetType = normalizeTargetType(targetType);
		validateTargetType(normalizedTargetType);

		List<Object[]> rows = likeRepository.findReactionSummaryRaw(targetId, normalizedTargetType);

		Map<String, Long> reactions = new LinkedHashMap<>();
		long totalCount = 0;
		List<String> topReactions = new ArrayList<>();

		for (Object[] row : rows) {
			String type = (String) row[0];
			Long count = (Long) row[1];
			reactions.put(type, count);
			totalCount += count;
			if (topReactions.size() < 3) {
				topReactions.add(type);
			}
		}

		return new ReactionSummaryResponse(targetId, normalizedTargetType, totalCount, reactions, topReactions);
	}

	/**
	 * Explicitly change an existing reaction to a new type.
	 * The total counter is NOT modified — the user is still just one reactor.
	 */
	@Override
	public Like changeReaction(int userId, int targetId, String targetType, String newReactionType) {
		validateUserId(userId);
		validateTargetId(targetId);
		String normalizedReactionType = normalizeReactionType(newReactionType);
		String normalizedTargetType = normalizeTargetType(targetType);
		validateReactionType(normalizedReactionType);
		validateTargetType(normalizedTargetType);

		Like existing = likeRepository.findByUserIdAndTargetIdAndTargetType(userId, targetId, normalizedTargetType)
				.orElseThrow(() -> new ResourceNotFoundException(
						"No reaction found for userId = " + userId + ", targetId = " + targetId));

		String oldType = existing.getReactionType();
		if (oldType.equals(normalizedReactionType)) {
			log.debug("changeReaction no-op: same type={}", normalizedReactionType);
			return existing;
		}

		existing.setReactionType(normalizedReactionType);
		Like updated = likeRepository.save(existing);
		log.info("Reaction changed: userId={}, targetId={}, {} -> {}", userId, targetId, oldType, normalizedReactionType);
		emitLikeNotificationIfNeeded(userId, targetId, normalizedTargetType, normalizedReactionType);
		return updated;
	}

	@Override
	@Transactional(readOnly = true)
	public Optional<Like> getUserReaction(int userId, int targetId, String targetType) {
		validateUserId(userId);
		validateTargetId(targetId);
		String normalizedTargetType = normalizeTargetType(targetType);
		validateTargetType(normalizedTargetType);
		return likeRepository.findByUserIdAndTargetIdAndTargetType(userId, targetId, normalizedTargetType);
	}

	// -------------------------------------------------------------------------
	// Validation helpers
	// -------------------------------------------------------------------------

	private void validateReactionType(String reactionType) {
		Set<String> valid = Set.of(
				Like.REACTION_LIKE, Like.REACTION_LOVE, Like.REACTION_HAHA,
				Like.REACTION_WOW, Like.REACTION_SAD, Like.REACTION_ANGRY);
		if (!valid.contains(reactionType)) {
			throw new IllegalArgumentException(
					"Invalid reactionType: '" + reactionType + "'. Must be one of: " + valid);
		}
	}

	private void validateTargetType(String targetType) {
		if (!Like.TARGET_POST.equals(targetType) && !Like.TARGET_COMMENT.equals(targetType)) {
			throw new IllegalArgumentException(
					"Invalid targetType: '" + targetType + "'. Must be POST or COMMENT.");
		}
	}

	private void validateTargetId(int targetId) {
		if (targetId <= 0) {
			throw new IllegalArgumentException("targetId must be greater than 0");
		}
	}

	private void emitLikeNotificationIfNeeded(int actorId, int targetId, String targetType, String reactionType) {
		try {
			Integer recipientId = resolveTargetOwnerId(targetId, targetType);
			if (recipientId == null || recipientId <= 0 || recipientId == actorId) {
				return;
			}

			String targetLabel = Like.TARGET_POST.equals(targetType) ? "post" : "comment";
			String message = "Someone reacted " + reactionType + " to your " + targetLabel + ".";

			CreateNotificationRequest request = new CreateNotificationRequest();
			request.setRecipientId(recipientId);
			request.setActorId(actorId);
			request.setType("LIKE");
			request.setMessage(message);
			request.setTargetId(targetId);
			request.setTargetType(targetType);

			notificationProducer.sendNotification(request);
		} catch (Exception ex) {
			log.warn("Failed to publish like notification: userId={}, targetId={}, targetType={}, reason={}",
					actorId, targetId, targetType, ex.getMessage());
		}
	}

	private Integer resolveTargetOwnerId(int targetId, String targetType) {
		if (Like.TARGET_POST.equals(targetType)) {
			PostSummaryDTO post = postServiceClient.getPostById(targetId);
			if (post == null || post.getAuthorId() == null) {
				return null;
			}
			return post.getAuthorId().intValue();
		}

		CommentSummaryDTO comment = commentServiceClient.getCommentById(targetId);
		return comment == null ? null : comment.getAuthorId();
	}

	private void validateUserId(int userId) {
		if (userId <= 0) {
			throw new IllegalArgumentException("Authenticated userId must be greater than 0");
		}
	}

	private String normalizeReactionType(String reactionType) {
		if (reactionType == null || reactionType.isBlank()) {
			throw new IllegalArgumentException("reactionType is required");
		}
		return reactionType.trim().toUpperCase(Locale.ROOT);
	}

	private String normalizeTargetType(String targetType) {
		if (targetType == null || targetType.isBlank()) {
			throw new IllegalArgumentException("targetType is required");
		}
		return targetType.trim().toUpperCase(Locale.ROOT);
	}

	// -------------------------------------------------------------------------
	// Counter sync helpers (total reactions, not per-type)
	// -------------------------------------------------------------------------

	private void incrementTargetCounter(int targetId, String targetType) {
		try {
			if (Like.TARGET_POST.equals(targetType)) {
				postServiceClient.incrementLikeCount(targetId);
				log.debug("Post-Service likesCount incremented: postId={}", targetId);
			} else if (Like.TARGET_COMMENT.equals(targetType)) {
				commentServiceClient.incrementCommentCount(targetId);
				log.debug("Comment-Service likesCount incremented: commentId={}", targetId);
			}
		} catch (Exception ex) {
			log.error("Counter increment failed: targetId={}, targetType={}: {}", targetId, targetType,
					ex.getMessage());
		}
	}

	private void decrementTargetCounter(int targetId, String targetType) {
		try {
			if (Like.TARGET_POST.equals(targetType)) {
				postServiceClient.decrementLikeCount(targetId);
				log.debug("Post-Service likesCount decremented: postId={}", targetId);
			} else if (Like.TARGET_COMMENT.equals(targetType)) {
				commentServiceClient.decrementCommentCount(targetId);
				log.debug("Comment-Service likesCount decremented: commentId={}", targetId);
			}
		} catch (Exception ex) {
			log.error("Counter decrement failed: targetId={}, targetType={}: {}", targetId, targetType,
					ex.getMessage());
		}
	}
}
