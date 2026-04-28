package com.connectsphere.follow.controller;

import com.connectsphere.follow.dto.ApiResponse;
import com.connectsphere.follow.dto.FollowRequestDTO;
import com.connectsphere.follow.dto.FollowResponseDTO;
import com.connectsphere.follow.service.FollowService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/follows")
public class FollowController {

    private static final Logger logger = LoggerFactory.getLogger(FollowController.class);

    private final FollowService followService;

    public FollowController(FollowService followService) {
        this.followService = followService;
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
    public ResponseEntity<ApiResponse<FollowResponseDTO>> followUser(@Valid @RequestBody FollowRequestDTO requestDTO) {
        Long followerId = getAuthenticatedUserId();
        logger.info("Received request to follow user {} by user: {}", requestDTO.getFolloweeId(), followerId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created("Successfully followed user", followService.followUser(requestDTO, followerId)));
    }

    @DeleteMapping
    public ResponseEntity<ApiResponse<Void>> unfollowUser(@RequestParam Long followeeId) {
        Long followerId = getAuthenticatedUserId();
        logger.info("Received request to unfollow user {} by user: {}", followeeId, followerId);
        followService.unfollowUser(followeeId, followerId);
        return ResponseEntity.ok(ApiResponse.success("Successfully unfollowed user", null));
    }

    @GetMapping("/isFollowing")
    public ResponseEntity<ApiResponse<Boolean>> isFollowing(@RequestParam Long followeeId) {
        Long followerId = getAuthenticatedUserId();
        logger.info("Received request to check if user {} is following {}", followerId, followeeId);
        return ResponseEntity.ok(ApiResponse.success("Follow status fetched successfully", followService.isFollowing(followerId, followeeId)));
    }

    @GetMapping("/followers/{userId}")
    public ResponseEntity<ApiResponse<List<FollowResponseDTO>>> getFollowers(@PathVariable Long userId) {
        logger.info("Received request to fetch followers for user: {}", userId);
        return ResponseEntity.ok(ApiResponse.success("Followers fetched successfully", followService.getFollowers(userId)));
    }

    @GetMapping("/following/{userId}")
    public ResponseEntity<ApiResponse<List<FollowResponseDTO>>> getFollowing(@PathVariable Long userId) {
        logger.info("Received request to fetch following for user: {}", userId);
        return ResponseEntity.ok(ApiResponse.success("Following fetched successfully", followService.getFollowing(userId)));
    }

    @GetMapping("/count/followers/{userId}")
    public ResponseEntity<ApiResponse<Long>> getFollowerCount(@PathVariable Long userId) {
        logger.info("Received request to fetch follower count for user: {}", userId);
        return ResponseEntity.ok(ApiResponse.success("Follower count fetched successfully", followService.getFollowerCount(userId)));
    }

    @GetMapping("/count/following/{userId}")
    public ResponseEntity<ApiResponse<Long>> getFollowingCount(@PathVariable Long userId) {
        logger.info("Received request to fetch following count for user: {}", userId);
        return ResponseEntity.ok(ApiResponse.success("Following count fetched successfully", followService.getFollowingCount(userId)));
    }

    @GetMapping("/mutual/{userId}")
    public ResponseEntity<ApiResponse<List<Long>>> getMutualFollows(@PathVariable Long userId) {
        logger.info("Received request to fetch mutual follows for user: {}", userId);
        return ResponseEntity.ok(ApiResponse.success("Mutual follows fetched successfully", followService.getMutualFollows(userId)));
    }

    @GetMapping("/mutual")
    public ResponseEntity<ApiResponse<List<Long>>> getMutualFollowsQuery(@RequestParam(required = false) Long userId) {
        Long resolvedUserId = userId != null ? userId : getAuthenticatedUserId();
        if (resolvedUserId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error(HttpStatus.UNAUTHORIZED.value(), "User not authenticated"));
        }
        return getMutualFollows(resolvedUserId);
    }

    @GetMapping("/suggestions/{userId}")
    public ResponseEntity<ApiResponse<List<Long>>> getSuggestions(@PathVariable Long userId) {
        logger.info("Received request to fetch suggestions for user: {}", userId);
        return ResponseEntity.ok(ApiResponse.success("Suggestions fetched successfully", followService.getSuggestions(userId)));
    }

    @GetMapping("/suggestions")
    public ResponseEntity<ApiResponse<List<Long>>> getSuggestionsQuery(@RequestParam(required = false) Long userId) {
        Long resolvedUserId = userId != null ? userId : getAuthenticatedUserId();
        if (resolvedUserId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error(HttpStatus.UNAUTHORIZED.value(), "User not authenticated"));
        }
        return getSuggestions(resolvedUserId);
    }

    @GetMapping("/suggested/{userId}")
    public ResponseEntity<ApiResponse<List<Long>>> getSuggestedUsers(@PathVariable Long userId) {
        return getSuggestions(userId);
    }
}
