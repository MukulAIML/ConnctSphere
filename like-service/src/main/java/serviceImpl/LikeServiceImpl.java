package com.connectsphere.like.serviceImpl;

import com.connectsphere.like.dto.LikeRequestDTO;
import com.connectsphere.like.dto.LikeResponseDTO;
import com.connectsphere.like.entity.LikeEntity;
import com.connectsphere.like.entity.ReactionType;
import com.connectsphere.like.entity.TargetType;
import com.connectsphere.like.exception.ResourceNotFoundException;
import com.connectsphere.like.repository.LikeRepository;
import com.connectsphere.like.service.LikeService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.connectsphere.like.dto.NotificationRequestDTO;

@Service
public class LikeServiceImpl implements LikeService {

    private static final Logger logger = LoggerFactory.getLogger(LikeServiceImpl.class);

    private final LikeRepository likeRepository;
    private final RestTemplate restTemplate;

    @Value("${post-service.url}")
    private String postServiceUrl;

    @Value("${notification-service.url}")
    private String notificationServiceUrl;

    @Value("${comment-service.url}")
    private String commentServiceUrl;

    public LikeServiceImpl(LikeRepository likeRepository, RestTemplate restTemplate) {
        this.likeRepository = likeRepository;
        this.restTemplate = restTemplate;
    }

    @Override
    @Transactional
    public LikeResponseDTO likeTarget(LikeRequestDTO requestDTO, Long userId) {
        Optional<LikeEntity> existingLike = likeRepository.findByUserIdAndTargetIdAndTargetType(
                userId, requestDTO.getTargetId(), requestDTO.getTargetType());

        if (existingLike.isPresent()) {
            // Update existing reaction
            LikeEntity like = existingLike.get();
            like.setReactionType(requestDTO.getReactionType());
            return mapToDTO(likeRepository.save(like));
        }

        // Create new like
        LikeEntity newLike = LikeEntity.builder()
                .userId(userId)
                .targetId(requestDTO.getTargetId())
                .targetType(requestDTO.getTargetType())
                .reactionType(requestDTO.getReactionType())
                .build();

        LikeEntity savedLike = likeRepository.save(newLike);

        // Notify post-service if the target is a POST
        if (requestDTO.getTargetType() == TargetType.POST) {
            updatePostLikeCount(requestDTO.getTargetId(), "increment");
        } else if (requestDTO.getTargetType() == TargetType.COMMENT) {
            updateCommentLikeCount(requestDTO.getTargetId(), "increment");
        }

        Long recipientId = resolveTargetOwnerId(savedLike.getTargetId(), savedLike.getTargetType());
        if (recipientId != null && !recipientId.equals(savedLike.getUserId())) {
            sendLikeNotification(savedLike, recipientId);
        }

        return mapToDTO(savedLike);
    }

    @Override
    @Transactional
    public void unlikeTarget(Long targetId, TargetType targetType, Long userId) {
        LikeEntity like = likeRepository.findByUserIdAndTargetIdAndTargetType(userId, targetId, targetType)
                .orElseThrow(() -> new ResourceNotFoundException("Like not found for this user and target"));

        likeRepository.delete(like);

        // Notify post-service if the target is a POST
        if (targetType == TargetType.POST) {
            updatePostLikeCount(targetId, "decrement");
        } else if (targetType == TargetType.COMMENT) {
            updateCommentLikeCount(targetId, "decrement");
        }
    }

    @Override
    @Transactional
    public LikeResponseDTO changeReaction(LikeRequestDTO requestDTO, Long userId) {
        return likeTarget(requestDTO, userId);
    }

    @Override
    public boolean hasUserLikedTarget(Long userId, Long targetId, TargetType targetType) {
        return likeRepository.findByUserIdAndTargetIdAndTargetType(userId, targetId, targetType).isPresent();
    }

    @Override
    public List<LikeResponseDTO> getLikesByTarget(Long targetId, TargetType targetType) {
        List<LikeEntity> likes = likeRepository.findByTargetIdAndTargetType(targetId, targetType);
        return likes.stream().map(this::mapToDTO).collect(Collectors.toList());
    }

    @Override
    public List<LikeResponseDTO> getLikesByUser(Long userId) {
        List<LikeEntity> likes = likeRepository.findByUserId(userId);
        return likes.stream().map(this::mapToDTO).collect(Collectors.toList());
    }

    @Override
    public long getLikesCountForTarget(Long targetId, TargetType targetType) {
        return likeRepository.countByTargetIdAndTargetType(targetId, targetType);
    }

    @Override
    public long getLikeCountByType(Long targetId, TargetType targetType, ReactionType reactionType) {
        return likeRepository.countByTargetIdAndTargetTypeAndReactionType(targetId, targetType, reactionType);
    }

    @Override
    public Map<String, Long> getReactionSummary(Long targetId, TargetType targetType) {
        List<LikeEntity> likes = likeRepository.findByTargetIdAndTargetType(targetId, targetType);
        return likes.stream()
                .collect(Collectors.groupingBy(
                        like -> like.getReactionType().name(),
                        Collectors.counting()
                ));
    }

    private void updatePostLikeCount(Long postId, String action) {
        try {
            String url = postServiceUrl + "/posts/" + postId + "/like/" + action;
            restTemplate.exchange(url, HttpMethod.PUT, null, Void.class);
            logger.info("Successfully requested like {} for postId: {}", action, postId);
        } catch (Exception e) {
            logger.error("Failed to {} like count in post-service for postId: {}: {}", action, postId, e.getMessage());
        }
    }

    private void updateCommentLikeCount(Long commentId, String action) {
        try {
            HttpMethod method = "increment".equalsIgnoreCase(action) ? HttpMethod.POST : HttpMethod.DELETE;
            String url = commentServiceUrl + "/comments/" + commentId + "/like";
            restTemplate.exchange(url, method, null, Void.class);
            logger.info("Successfully requested comment like {} for commentId: {}", action, commentId);
        } catch (Exception e) {
            logger.error("Failed to {} comment like count for commentId {}: {}", action, commentId, e.getMessage());
        }
    }

    private Long resolveTargetOwnerId(Long targetId, TargetType targetType) {
        try {
            String url = targetType == TargetType.POST
                    ? postServiceUrl + "/posts/" + targetId
                    : commentServiceUrl + "/comments/" + targetId;

            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, null, Map.class);
            Long authorId = extractAuthorId(response.getBody());
            if (authorId != null) {
                return authorId;
            }
        } catch (Exception e) {
            logger.error("Failed to resolve target owner for {} {}: {}", targetType, targetId, e.getMessage());
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

    private void sendLikeNotification(LikeEntity like, Long recipientId) {
        try {
            String url = notificationServiceUrl + "/notifications";
            
            NotificationRequestDTO notification = NotificationRequestDTO.builder()
                    .recipientId(recipientId)
                    .actorId(like.getUserId())
                    .type("LIKE")
                    .message("User " + like.getUserId() + " reacted with " + like.getReactionType().name() + " on your " + like.getTargetType().name().toLowerCase())
                    .targetId(like.getTargetId())
                    .targetType(like.getTargetType().name())
                    .build();

            restTemplate.exchange(url, HttpMethod.POST, new HttpEntity<>(notification), Void.class);
            logger.info("Successfully triggered notification for likeId: {}", like.getLikeId());
        } catch (Exception e) {
            logger.error("Failed to trigger notification for likeId: {}: {}", like.getLikeId(), e.getMessage());
        }
    }

    private LikeResponseDTO mapToDTO(LikeEntity likeEntity) {
        return LikeResponseDTO.builder()
                .likeId(likeEntity.getLikeId())
                .userId(likeEntity.getUserId())
                .targetId(likeEntity.getTargetId())
                .targetType(likeEntity.getTargetType())
                .reactionType(likeEntity.getReactionType())
                .createdAt(likeEntity.getCreatedAt())
                .build();
    }
}
