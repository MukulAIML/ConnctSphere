package com.connectsphere.like.controller;

import com.connectsphere.like.dto.ApiResponse;
import com.connectsphere.like.dto.LikeRequestDTO;
import com.connectsphere.like.dto.LikeResponseDTO;
import com.connectsphere.like.entity.ReactionType;
import com.connectsphere.like.entity.TargetType;
import com.connectsphere.like.service.LikeService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/likes")
public class LikeController {

    private static final Logger logger = LoggerFactory.getLogger(LikeController.class);

    private final LikeService likeService;

    public LikeController(LikeService likeService) {
        this.likeService = likeService;
    }

    private Long getAuthenticatedUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() != null) {
            try {
                return Long.valueOf(authentication.getPrincipal().toString());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<LikeResponseDTO>> likeTarget(@Valid @RequestBody LikeRequestDTO requestDTO) {
        Long userId = getAuthenticatedUserId();
        logger.info("Received request to like target by user: {}", userId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created("Target liked successfully", likeService.likeTarget(requestDTO, userId)));
    }

    @PostMapping(params = "targetId")
    public ResponseEntity<ApiResponse<LikeResponseDTO>> likeTargetByQuery(
            @RequestParam Long targetId,
            @RequestParam(defaultValue = "POST") TargetType targetType,
            @RequestParam(defaultValue = "LIKE") ReactionType reactionType
    ) {
        return likeTarget(LikeRequestDTO.builder()
                .targetId(targetId)
                .targetType(targetType)
                .reactionType(reactionType)
                .build());
    }

    @DeleteMapping("/target/{targetId}")
    public ResponseEntity<ApiResponse<Void>> unlikeTarget(@PathVariable Long targetId, @RequestParam TargetType targetType) {
        Long userId = getAuthenticatedUserId();
        logger.info("Received request to unlike target {} of type {} by user: {}", targetId, targetType, userId);
        likeService.unlikeTarget(targetId, targetType, userId);
        return ResponseEntity.ok(ApiResponse.success("Target unliked successfully", null));
    }

    // Explicitly added to match frontend requirement
    @DeleteMapping
    public ResponseEntity<ApiResponse<Void>> unlikeTargetQuery(
            @RequestParam Long targetId,
            @RequestParam(defaultValue = "POST") TargetType targetType
    ) {
        return unlikeTarget(targetId, targetType);
    }

    @PutMapping("/reaction")
    public ResponseEntity<ApiResponse<LikeResponseDTO>> changeReaction(@Valid @RequestBody LikeRequestDTO requestDTO) {
        Long userId = getAuthenticatedUserId();
        logger.info("Received request to change reaction by user: {}", userId);
        return ResponseEntity.ok(ApiResponse.success("Reaction changed successfully", likeService.changeReaction(requestDTO, userId)));
    }

    @PutMapping
    public ResponseEntity<ApiResponse<LikeResponseDTO>> changeReactionByQuery(
            @RequestParam Long targetId,
            @RequestParam(defaultValue = "POST") TargetType targetType,
            @RequestParam ReactionType reactionType
    ) {
        return changeReaction(LikeRequestDTO.builder()
                .targetId(targetId)
                .targetType(targetType)
                .reactionType(reactionType)
                .build());
    }

    @GetMapping("/hasLiked")
    public ResponseEntity<ApiResponse<Boolean>> hasUserLikedTarget(@RequestParam Long targetId, @RequestParam TargetType targetType) {
        Long userId = getAuthenticatedUserId();
        logger.info("Received request to check if user {} liked target {}", userId, targetId);
        return ResponseEntity.ok(ApiResponse.success("Like status fetched successfully", likeService.hasUserLikedTarget(userId, targetId, targetType)));
    }

    @GetMapping("/target/{targetId}")
    public ResponseEntity<ApiResponse<List<LikeResponseDTO>>> getLikesByTarget(@PathVariable Long targetId, @RequestParam TargetType targetType) {
        logger.info("Received request to fetch likes for target: {}", targetId);
        return ResponseEntity.ok(ApiResponse.success("Likes fetched successfully", likeService.getLikesByTarget(targetId, targetType)));
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<ApiResponse<List<LikeResponseDTO>>> getLikesByUser(@PathVariable Long userId) {
        logger.info("Received request to fetch likes by user: {}", userId);
        return ResponseEntity.ok(ApiResponse.success("User likes fetched successfully", likeService.getLikesByUser(userId)));
    }

    @GetMapping("/count/{targetId}")
    public ResponseEntity<ApiResponse<Long>> getLikesCountForTarget(@PathVariable Long targetId, @RequestParam TargetType targetType) {
        logger.info("Received request to fetch like count for target: {}", targetId);
        return ResponseEntity.ok(ApiResponse.success("Like count fetched successfully", likeService.getLikesCountForTarget(targetId, targetType)));
    }

    @GetMapping("/count")
    public ResponseEntity<ApiResponse<Long>> getLikesCountForTargetQuery(
            @RequestParam Long targetId,
            @RequestParam(defaultValue = "POST") TargetType targetType
    ) {
        return getLikesCountForTarget(targetId, targetType);
    }

    @GetMapping("/count/{targetId}/type")
    public ResponseEntity<ApiResponse<Long>> getLikesCountByReactionType(
            @PathVariable Long targetId,
            @RequestParam TargetType targetType,
            @RequestParam ReactionType reactionType
    ) {
        logger.info("Received request to fetch {} count for target {}", reactionType, targetId);
        return ResponseEntity.ok(ApiResponse.success(
                "Reaction type count fetched successfully",
                likeService.getLikeCountByType(targetId, targetType, reactionType)
        ));
    }

    @GetMapping("/summary/{targetId}")
    public ResponseEntity<ApiResponse<Map<String, Long>>> getReactionSummary(@PathVariable Long targetId, @RequestParam TargetType targetType) {
        logger.info("Received request to fetch reaction summary for target: {}", targetId);
        return ResponseEntity.ok(ApiResponse.success("Reaction summary fetched successfully", likeService.getReactionSummary(targetId, targetType)));
    }

    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<Map<String, Long>>> getReactionSummaryQuery(
            @RequestParam Long targetId,
            @RequestParam(defaultValue = "POST") TargetType targetType
    ) {
        return getReactionSummary(targetId, targetType);
    }
}
