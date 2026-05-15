package com.connectsphere.commentservice.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.connectsphere.commentservice.client.PostServiceClient;
import com.connectsphere.commentservice.dto.CreateCommentRequest;
import com.connectsphere.commentservice.dto.PostResponseDTO;
import com.connectsphere.commentservice.entity.Comment;
import com.connectsphere.commentservice.entity.CommentLike;
import com.connectsphere.commentservice.exception.ResourceNotFoundException;
import com.connectsphere.commentservice.messaging.NotificationEventProducer;
import com.connectsphere.commentservice.repository.CommentLikeRepository;
import com.connectsphere.commentservice.repository.CommentRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class CommentServiceImpl implements CommentService {

	private final CommentRepository commentRepository;
	private final CommentLikeRepository commentLikeRepository;
	private final PostServiceClient postServiceClient;
	private final NotificationEventProducer notificationEventProducer;

	@Override
	public Comment addComment(CreateCommentRequest request) {
		if (request.getAuthorId() == null) {
			throw new IllegalArgumentException("Authenticated user is required to create a comment.");
		}
		if (request.getPostId() == null) {
			throw new IllegalArgumentException("postId is required.");
		}
		if (request.getPostId() <= 0) {
			throw new IllegalArgumentException("postId must be greater than 0.");
		}

		PostResponseDTO post = requireVisiblePost(request.getPostId());

		log.info("Adding top-level comment: postId={}, authorId={}", request.getPostId(), request.getAuthorId());

		Comment comment = Comment.builder().postId(request.getPostId()).authorId(request.getAuthorId())
				.content(request.getContent()).parentCommentId(null)
				.likesCount(0).isDeleted(false).build();

		Comment saved = commentRepository.save(comment);
		log.info("Comment saved: commentId={}", saved.getCommentId());
		incrementPostCommentCount(request.getPostId());

		// Notify post author asynchronously.
		try {
			if (post != null && post.getAuthorId() != null && post.getAuthorId().intValue() != request.getAuthorId()) {
				notificationEventProducer.publish(
						post.getAuthorId(),
						Long.valueOf(request.getAuthorId()),
						"COMMENT",
						"Someone commented on your post.",
						Long.valueOf(request.getPostId()),
						"POST"
				);
			}
		} catch (Exception e) {
			log.error("Failed to send notification for comment: {}", e.getMessage());
		}

		return saved;
	}

	@Override
	public Comment addReply(int parentCommentId, CreateCommentRequest request) {
		if (request.getAuthorId() == null) {
			throw new IllegalArgumentException("Authenticated user is required to create a reply.");
		}

		log.info("Adding reply to commentId={}, authorId={}", parentCommentId, request.getAuthorId());
		Comment parent = requireComment(parentCommentId);
		if (parent.isReply()) {
			throw new IllegalArgumentException(
					"Replies to replies are not allowed. ConnectSphere supports two-level threading only. "
							+ "Target commentId=" + parentCommentId + " is itself a reply.");
		}
		if (request.getPostId() != null && request.getPostId() != parent.getPostId()) {
			throw new IllegalArgumentException("postId mismatch for reply creation.");
		}

		Comment reply = Comment.builder().postId(parent.getPostId()).authorId(request.getAuthorId())
				.content(request.getContent()).parentCommentId(parentCommentId).likesCount(0).isDeleted(false).build();

		Comment saved = commentRepository.save(reply);
		log.info("Reply saved: commentId={}, parentCommentId={}", saved.getCommentId(), parentCommentId);

		incrementPostCommentCount(parent.getPostId());

		// Notify parent comment author asynchronously.
		try {
			if (parent.getAuthorId() != request.getAuthorId()) {
				notificationEventProducer.publish(
						Long.valueOf(parent.getAuthorId()),
						Long.valueOf(request.getAuthorId()),
						"REPLY",
						"Someone replied to your comment.",
						Long.valueOf(parentCommentId),
						"COMMENT"
				);
			}
		} catch (Exception e) {
			log.error("Failed to send notification for reply: {}", e.getMessage());
		}

		return saved;
	}

	@Override
	@Transactional(readOnly = true)
	public List<Comment> getCommentsByPost(int postId) {
		List<Comment> topLevelComments = commentRepository.findTopLevelByPostId(postId);
		List<Comment> replies = commentRepository.findByPostIdAndParentCommentIdIsNotNullAndIsDeletedFalseOrderByCreatedAtAsc(postId);
		Map<Integer, List<Comment>> repliesByParent = new LinkedHashMap<>();
		for (Comment reply : replies) {
			repliesByParent.computeIfAbsent(reply.getParentCommentId(), ignored -> new ArrayList<>()).add(reply);
		}
		for (Comment comment : topLevelComments) {
			comment.setReplies(repliesByParent.getOrDefault(comment.getCommentId(), new ArrayList<>()));
		}
		return topLevelComments;
	}

	@Override
	@Transactional(readOnly = true)
	public Comment getCommentById(int commentId) {
		return requireComment(commentId);
	}

	@Override
	@Transactional(readOnly = true)
	public List<Comment> getReplies(int parentCommentId) {
		requireComment(parentCommentId);
		List<Comment> replies = commentRepository.findByParentCommentIdAndIsDeletedFalseOrderByCreatedAtAsc(parentCommentId);
		for (Comment reply : replies) {
			reply.setReplies(new ArrayList<>());
		}
		return replies;
	}

	@Override
	@Transactional(readOnly = true)
	public List<Comment> getCommentsByUser(int userId) {
		return commentRepository.findByAuthorIdAndIsDeletedFalseOrderByCreatedAtDesc(userId);
	}

	@Override
	public Comment updateComment(int commentId, String content) {
		if (content == null || content.isBlank()) {
			throw new IllegalArgumentException("Comment content cannot be blank.");
		}
		Comment comment = requireComment(commentId);
		comment.setContent(content.trim());
		Comment updated = commentRepository.save(comment);
		log.info("Comment updated: commentId={}", commentId);
		return updated;
	}

	@Override
	public void deleteComment(int commentId) {
		Comment comment = requireComment(commentId);

		commentRepository.softDeleteByCommentId(commentId);
		commentLikeRepository.deleteByCommentId(commentId);
		log.info("Comment soft-deleted: commentId={}", commentId);

		int replyCount = 0;
		if (!comment.isReply()) {
			List<Comment> replies = commentRepository.findByParentCommentIdAndIsDeletedFalseOrderByCreatedAtAsc(commentId);
			replyCount = replies.size();
			if (replyCount > 0) {
				commentRepository.softDeleteRepliesByParentCommentId(commentId);
				for (Comment reply : replies) {
					commentLikeRepository.deleteByCommentId(reply.getCommentId());
				}
				log.info("Cascaded soft-delete to {} replies of commentId={}", replyCount, commentId);
			}
		}

		int totalDeleted = 1 + replyCount;
		for (int i = 0; i < totalDeleted; i++) {
			decrementPostCommentCount(comment.getPostId());
		}
	}

	@Override
	public Comment likeComment(int commentId, Integer userId) {
		if (userId == null) {
			int updated = commentRepository.incrementLikesCount(commentId);
			if (updated == 0) {
				throw new ResourceNotFoundException("Comment not found or has been deleted: commentId=" + commentId);
			}
			log.debug("likesCount incremented for commentId={} (system call)", commentId);
			return requireComment(commentId);
		}

		requireComment(commentId);

		commentLikeRepository.findByCommentIdAndUserId(commentId, userId).ifPresentOrElse(existingLike -> {
			commentLikeRepository.delete(existingLike);
			commentRepository.decrementLikesCount(commentId);
			log.debug("Comment like toggled OFF for commentId={}, userId={}", commentId, userId);
		}, () -> {
			try {
				commentLikeRepository.save(CommentLike.builder().commentId(commentId).userId(userId).build());
				commentRepository.incrementLikesCount(commentId);
				log.debug("Comment like toggled ON for commentId={}, userId={}", commentId, userId);
			} catch (DataIntegrityViolationException ex) {
				log.debug("Duplicate comment like prevented for commentId={}, userId={}", commentId, userId);
			}
		});
		return requireComment(commentId);
	}

	@Override
	public Comment unlikeComment(int commentId, Integer userId) {
		if (userId == null) {
			int updated = commentRepository.decrementLikesCount(commentId);
			if (updated == 0) {
				throw new ResourceNotFoundException("Comment not found or has been deleted: commentId=" + commentId);
			}
			log.debug("likesCount decremented for commentId={} (system call)", commentId);
			return requireComment(commentId);
		}

		requireComment(commentId);

		commentLikeRepository.findByCommentIdAndUserId(commentId, userId).ifPresent(commentLike -> {
			commentLikeRepository.delete(commentLike);
			commentRepository.decrementLikesCount(commentId);
			log.debug("Comment like removed for commentId={}, userId={}", commentId, userId);
		});
		return requireComment(commentId);
	}

	@Override
	@Transactional(readOnly = true)
	public int getCommentCount(int postId) {
		return commentRepository.countByPostIdAndIsDeletedFalse(postId);
	}

	private PostResponseDTO requireVisiblePost(int postId) {
		try {
			PostResponseDTO post = postServiceClient.getPostById(postId);
			if (post == null || post.getAuthorId() == null) {
				throw new IllegalArgumentException("Invalid postId: " + postId);
			}
			return post;
		} catch (Exception ex) {
			throw new IllegalArgumentException("Invalid or inaccessible postId: " + postId);
		}
	}

	private Comment requireComment(int commentId) {
		return commentRepository.findByCommentIdAndIsDeletedFalse(commentId).orElseThrow(
				() -> new ResourceNotFoundException("Comment not found or has been deleted: commentId=" + commentId));
	}

	private void incrementPostCommentCount(int postId) {
		try {
			postServiceClient.incrementCommentCount(postId);
			log.debug("Post-Service commentsCount incremented for postId={}", postId);
		} catch (Exception ex) {
			log.error("Failed to increment commentsCount for postId={}: {}", postId, ex.getMessage());
		}
	}

	private void decrementPostCommentCount(int postId) {
		try {
			postServiceClient.decrementCommentCount(postId);
			log.debug("Post-Service commentsCount decremented for postId={}", postId);
		} catch (Exception ex) {
			log.error("Failed to decrement commentsCount for postId={}: {}", postId, ex.getMessage());
		}
	}
}
