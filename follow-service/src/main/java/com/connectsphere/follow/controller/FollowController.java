package com.connectsphere.follow.controller;

import com.connectsphere.follow.dto.ApiResponse;
import com.connectsphere.follow.dto.FollowRequestDTO;
import com.connectsphere.follow.dto.FollowResponseDTO;
import com.connectsphere.follow.service.FollowService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

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
@Tag(name = "Follow Relationships", description = "Manage follow/unfollow relationships between ConnectSphere users")
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

    @Operation(
        summary = "Follow a user",
        description = "Create a follow relationship from the authenticated user to the specified followee.",
        security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Follow relationship created",
                content = @Content(schema = @Schema(implementation = FollowResponseDTO.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid request body", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Not authenticated", content = @Content)
    })
    @PostMapping
    public ResponseEntity<ApiResponse<FollowResponseDTO>> followUser(@Valid @RequestBody FollowRequestDTO requestDTO) {
        Long followerId = getAuthenticatedUserId();
        if (followerId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error(HttpStatus.UNAUTHORIZED.value(), "User not authenticated"));
        }
        logger.info("Received request to follow user {} by user: {}", requestDTO.getFolloweeId(), followerId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created("Successfully followed user", followService.followUser(requestDTO, followerId)));
    }

    @Operation(
        summary = "Unfollow a user",
        description = "Remove the follow relationship from the authenticated user to the specified followee.",
        security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Unfollowed successfully"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Not authenticated", content = @Content)
    })
    @DeleteMapping
    public ResponseEntity<ApiResponse<Void>> unfollowUser(
            @Parameter(description = "ID of the user to unfollow", required = true) @RequestParam Long followeeId) {
        Long followerId = getAuthenticatedUserId();
        if (followerId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error(HttpStatus.UNAUTHORIZED.value(), "User not authenticated"));
        }
        logger.info("Received request to unfollow user {} by user: {}", followeeId, followerId);
        followService.unfollowUser(followeeId, followerId);
        return ResponseEntity.ok(ApiResponse.success("Successfully unfollowed user", null));
    }

    @Operation(
        summary = "Check if the current user is following another user",
        description = "Returns true if the authenticated user follows the specified followee.",
        security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Follow status",
                content = @Content(schema = @Schema(example = "{\"status\":200,\"message\":\"Follow status fetched successfully\",\"data\":true}"))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Not authenticated", content = @Content)
    })
    @GetMapping("/isFollowing")
    public ResponseEntity<ApiResponse<Boolean>> isFollowing(
            @Parameter(description = "ID of the user to check", required = true) @RequestParam Long followeeId) {
        Long followerId = getAuthenticatedUserId();
        if (followerId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error(HttpStatus.UNAUTHORIZED.value(), "User not authenticated"));
        }
        logger.info("Received request to check if user {} is following {}", followerId, followeeId);
        return ResponseEntity.ok(ApiResponse.success("Follow status fetched successfully", followService.isFollowing(followerId, followeeId)));
    }

    @Operation(
        summary = "Get followers of a user",
        description = "Returns the list of users who follow the specified user. Publicly accessible."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "List of followers")
    @GetMapping("/followers/{userId}")
    public ResponseEntity<ApiResponse<List<FollowResponseDTO>>> getFollowers(
            @Parameter(description = "ID of the user", required = true) @PathVariable Long userId) {
        logger.info("Received request to fetch followers for user: {}", userId);
        return ResponseEntity.ok(ApiResponse.success("Followers fetched successfully", followService.getFollowers(userId)));
    }

    @Operation(
        summary = "Get users that a user is following",
        description = "Returns the list of users the specified user follows. Publicly accessible."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "List of following")
    @GetMapping("/following/{userId}")
    public ResponseEntity<ApiResponse<List<FollowResponseDTO>>> getFollowing(
            @Parameter(description = "ID of the user", required = true) @PathVariable Long userId) {
        logger.info("Received request to fetch following for user: {}", userId);
        return ResponseEntity.ok(ApiResponse.success("Following fetched successfully", followService.getFollowing(userId)));
    }

    @Operation(
        summary = "Get follower count for a user",
        description = "Returns the total number of users following the specified user.",
        security = @SecurityRequirement(name = "bearerAuth")
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Follower count")
    @GetMapping("/count/followers/{userId}")
    public ResponseEntity<ApiResponse<Long>> getFollowerCount(
            @Parameter(description = "ID of the user", required = true) @PathVariable Long userId) {
        logger.info("Received request to fetch follower count for user: {}", userId);
        return ResponseEntity.ok(ApiResponse.success("Follower count fetched successfully", followService.getFollowerCount(userId)));
    }

    @Operation(
        summary = "Get following count for a user",
        description = "Returns the total number of users that the specified user follows.",
        security = @SecurityRequirement(name = "bearerAuth")
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Following count")
    @GetMapping("/count/following/{userId}")
    public ResponseEntity<ApiResponse<Long>> getFollowingCount(
            @Parameter(description = "ID of the user", required = true) @PathVariable Long userId) {
        logger.info("Received request to fetch following count for user: {}", userId);
        return ResponseEntity.ok(ApiResponse.success("Following count fetched successfully", followService.getFollowingCount(userId)));
    }

    @Operation(
        summary = "Get mutual follows for a user (path variable)",
        description = "Returns user IDs who both follow and are followed by the specified user.",
        security = @SecurityRequirement(name = "bearerAuth")
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "List of mutual follow user IDs")
    @GetMapping("/mutual/{userId}")
    public ResponseEntity<ApiResponse<List<Long>>> getMutualFollows(
            @Parameter(description = "ID of the user", required = true) @PathVariable Long userId) {
        logger.info("Received request to fetch mutual follows for user: {}", userId);
        return ResponseEntity.ok(ApiResponse.success("Mutual follows fetched successfully", followService.getMutualFollows(userId)));
    }

    @Operation(
        summary = "Get mutual follows (query param or current user)",
        description = "If userId is omitted, resolves to the authenticated user. Returns mutual follow user IDs.",
        security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "List of mutual follow user IDs"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Not authenticated", content = @Content)
    })
    @GetMapping("/mutual")
    public ResponseEntity<ApiResponse<List<Long>>> getMutualFollowsQuery(
            @Parameter(description = "User ID (defaults to authenticated user)") @RequestParam(required = false) Long userId) {
        Long resolvedUserId = userId != null ? userId : getAuthenticatedUserId();
        if (resolvedUserId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error(HttpStatus.UNAUTHORIZED.value(), "User not authenticated"));
        }
        return getMutualFollows(resolvedUserId);
    }

    @Operation(
        summary = "Get follow suggestions for a user (path variable)",
        description = "Returns user IDs of people the specified user might want to follow (friends-of-friends logic).",
        security = @SecurityRequirement(name = "bearerAuth")
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "List of suggested user IDs")
    @GetMapping("/suggestions/{userId}")
    public ResponseEntity<ApiResponse<List<Long>>> getSuggestions(
            @Parameter(description = "ID of the user", required = true) @PathVariable Long userId) {
        logger.info("Received request to fetch suggestions for user: {}", userId);
        return ResponseEntity.ok(ApiResponse.success("Suggestions fetched successfully", followService.getSuggestions(userId)));
    }

    @Operation(
        summary = "Get follow suggestions (query param or current user)",
        description = "If userId is omitted, resolves to the authenticated user.",
        security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "List of suggested user IDs"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Not authenticated", content = @Content)
    })
    @GetMapping("/suggestions")
    public ResponseEntity<ApiResponse<List<Long>>> getSuggestionsQuery(
            @Parameter(description = "User ID (defaults to authenticated user)") @RequestParam(required = false) Long userId) {
        Long resolvedUserId = userId != null ? userId : getAuthenticatedUserId();
        if (resolvedUserId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error(HttpStatus.UNAUTHORIZED.value(), "User not authenticated"));
        }
        return getSuggestions(resolvedUserId);
    }

    @Operation(
        summary = "Get suggested users (alias endpoint)",
        description = "Alias for GET /suggestions/{userId}.",
        security = @SecurityRequirement(name = "bearerAuth")
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "List of suggested user IDs")
    @GetMapping("/suggested/{userId}")
    public ResponseEntity<ApiResponse<List<Long>>> getSuggestedUsers(
            @Parameter(description = "ID of the user", required = true) @PathVariable Long userId) {
        return getSuggestions(userId);
    }
}
