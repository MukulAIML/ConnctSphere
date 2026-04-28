package com.connectsphere.comment.serviceImpl;

import com.connectsphere.comment.dto.CommentRequestDTO;
import com.connectsphere.comment.dto.CommentResponseDTO;
import com.connectsphere.comment.entity.Comment;
import com.connectsphere.comment.exception.BadRequestException;
import com.connectsphere.comment.exception.ResourceNotFoundException;
import com.connectsphere.comment.exception.UnauthorizedAccessException;
import com.connectsphere.comment.repository.CommentRepository;
import com.connectsphere.comment.service.CommentService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.connectsphere.comment.dto.NotificationRequestDTO;

@Service
public class CommentServiceImpl implements CommentService {

    private static final Logger logger = LoggerFactory.getLogger(CommentServiceImpl.class);

    private final CommentRepository commentRepository;
    private final RestTemplate restTemplate;

    @Value("${post-service.url}")
    private String postServiceUrl;
    
    @Value("${notification-service.url}")
    private String notificationServiceUrl;

    public CommentServiceImpl(CommentRepository commentRepository, RestTemplate restTemplate) {
        this.commentRepository = commentRepository;
        this.restTemplate = restTemplate;
    }

    @Override
    public CommentResponseDTO createComment(CommentRequestDTO commentRequestDTO, Long authorId) {
        if (authorId == null) {
            throw new UnauthorizedAccessException("User must be authenticated to create a comment");
        }

        Comment parentComment = null;
        if (commentRequestDTO.getParentCommentId() != null) {
            parentComment = getCommentEntityById(commentRequestDTO.getParentCommentId());
            if (!parentComment.getPostId().equals(commentRequestDTO.getPostId())) {
                throw new BadRequestException("Reply must belong to the same post");
            }
            if (parentComment.getParentCommentId() != null) {
                throw new BadRequestException("Only two-level threaded discussions are allowed");
            }
        }

        Comment comment = Comment.builder()
                .postId(commentRequestDTO.getPostId())
                .authorId(authorId)
                .parentCommentId(commentRequestDTO.getParentCommentId())
                .content(commentRequestDTO.getContent())
                .likesCount(0)
                .isDeleted(false)
                .build();

        Comment savedComment = commentRepository.save(comment);

        // Call post-service to increment comment count
        incrementPostCommentCount(savedComment.getPostId());

        // Call notification-service
        Long recipientId = parentComment == null ? resolvePostAuthorId(savedComment.getPostId()) : parentComment.getAuthorId();
        if (recipientId != null && !recipientId.equals(savedComment.getAuthorId())) {
            boolean isReply = parentComment != null;
            sendCommentNotification(
                    savedComment,
                    recipientId,
                    isReply ? parentComment.getCommentId() : savedComment.getPostId(),
                    isReply ? "COMMENT" : "POST",
                    isReply
                            ? "User " + savedComment.getAuthorId() + " replied to your comment"
                            : "User " + savedComment.getAuthorId() + " commented on your post",
                    isReply ? "REPLY" : "COMMENT"
            );
        }

        return mapToDTO(savedComment);
    }

    private void incrementPostCommentCount(Long postId) {
        updatePostCommentCount(postId, "increment");
    }

    private void decrementPostCommentCount(Long postId) {
        updatePostCommentCount(postId, "decrement");
    }

    private void updatePostCommentCount(Long postId, String action) {
        try {
            String url = postServiceUrl + "/posts/" + postId + "/comment/" + action;
            restTemplate.exchange(url, HttpMethod.PUT, null, Void.class);
            logger.info("Successfully requested comment {} in post-service for postId: {}", action, postId);
        } catch (Exception e) {
            logger.error("Failed to {} comment count in post-service for postId: {}: {}", action, postId, e.getMessage());
        }
    }

    private Long resolvePostAuthorId(Long postId) {
        try {
            String url = postServiceUrl + "/posts/" + postId;
            ResponseEntity<Map> postResponse = restTemplate.exchange(url, HttpMethod.GET, null, Map.class);
            Long authorId = extractAuthorId(postResponse.getBody());
            if (authorId != null) {
                return authorId;
            }
        } catch (Exception e) {
            logger.error("Failed to resolve post author for postId {}: {}", postId, e.getMessage());
        }
        return null;
    }

    private Long extractAuthorId(Map responseBody) {
        if (responseBody == null) {
            return null;
        }

        Object directAuthorId = responseBody.get("authorId");
        if (directAuthorId != null) {
            return Long.valueOf(directAuthorId.toString());
        }

        Object nestedData = responseBody.get("data");
        if (nestedData instanceof Map nestedDataMap && nestedDataMap.get("authorId") != null) {
            return Long.valueOf(nestedDataMap.get("authorId").toString());
        }

        return null;
    }

    private void sendCommentNotification(
            Comment comment,
            Long recipientId,
            Long targetId,
            String targetType,
            String message,
            String notificationType
    ) {
        try {
            String url = notificationServiceUrl + "/notifications";
            NotificationRequestDTO notification = NotificationRequestDTO.builder()
                    .recipientId(recipientId)
                    .actorId(comment.getAuthorId())
                    .type(notificationType)
                    .message(message)
                    .targetId(targetId)
                    .targetType(targetType)
                    .build();

            restTemplate.exchange(url, HttpMethod.POST, new HttpEntity<>(notification), Void.class);
            logger.info("Successfully triggered notification for commentId: {}", comment.getCommentId());
        } catch (Exception e) {
            logger.error("Failed to trigger notification for commentId: {}: {}", comment.getCommentId(), e.getMessage());
        }
    }

    @Override
    public CommentResponseDTO getCommentById(Long commentId) {
        Comment comment = getCommentEntityById(commentId);
        return mapToDTO(comment);
    }

    @Override
    public List<CommentResponseDTO> getCommentsByPostId(Long postId) {
        List<Comment> comments = commentRepository.findByPostIdAndParentCommentIdIsNullAndIsDeletedFalseOrderByCreatedAtDesc(postId);
        return comments.stream().map(this::mapToDTO).collect(Collectors.toList());
    }

    @Override
    public List<CommentResponseDTO> getRepliesByParentId(Long parentId) {
        List<Comment> comments = commentRepository.findByParentCommentIdAndIsDeletedFalseOrderByCreatedAtAsc(parentId);
        return comments.stream().map(this::mapToDTO).collect(Collectors.toList());
    }

    @Override
    public List<CommentResponseDTO> getCommentsByUser(Long authorId) {
        List<Comment> comments = commentRepository.findByAuthorIdAndIsDeletedFalseOrderByCreatedAtDesc(authorId);
        return comments.stream().map(this::mapToDTO).collect(Collectors.toList());
    }

    @Override
    public CommentResponseDTO updateComment(Long commentId, CommentRequestDTO commentRequestDTO, Long authorId) {
        Comment comment = getCommentEntityById(commentId);
        validateAuthor(comment, authorId);

        comment.setContent(commentRequestDTO.getContent());
        Comment updatedComment = commentRepository.save(comment);
        return mapToDTO(updatedComment);
    }

    @Override
    public void deleteComment(Long commentId, Long authorId) {
        Comment comment = getCommentEntityById(commentId);
        validateAuthor(comment, authorId);

        comment.setIsDeleted(true);
        commentRepository.save(comment);
        decrementPostCommentCount(comment.getPostId());
    }

    @Override
    public CommentResponseDTO likeComment(Long commentId) {
        Comment comment = getCommentEntityById(commentId);
        comment.setLikesCount(comment.getLikesCount() + 1);
        return mapToDTO(commentRepository.save(comment));
    }

    @Override
    public CommentResponseDTO unlikeComment(Long commentId) {
        Comment comment = getCommentEntityById(commentId);
        if (comment.getLikesCount() > 0) {
            comment.setLikesCount(comment.getLikesCount() - 1);
        }
        return mapToDTO(commentRepository.save(comment));
    }

    @Override
    public long getCommentCountForPost(Long postId) {
        return commentRepository.countByPostIdAndIsDeletedFalse(postId);
    }

    private Comment getCommentEntityById(Long commentId) {
        return commentRepository.findById(commentId)
                .filter(c -> !c.getIsDeleted())
                .orElseThrow(() -> new ResourceNotFoundException("Comment not found with id: " + commentId));
    }

    private void validateAuthor(Comment comment, Long authorId) {
        if (!comment.getAuthorId().equals(authorId)) {
            throw new UnauthorizedAccessException("You are not authorized to perform this action on this comment");
        }
    }

    private CommentResponseDTO mapToDTO(Comment comment) {
        return CommentResponseDTO.builder()
                .commentId(comment.getCommentId())
                .postId(comment.getPostId())
                .authorId(comment.getAuthorId())
                .parentCommentId(comment.getParentCommentId())
                .content(comment.getContent())
                .likesCount(comment.getLikesCount())
                .createdAt(comment.getCreatedAt())
                .updatedAt(comment.getUpdatedAt())
                .build();
    }
}
