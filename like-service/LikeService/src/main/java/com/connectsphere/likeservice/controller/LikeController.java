package com.connectsphere.likeservice.controller;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.connectsphere.likeservice.dto.LikeRequest;
import com.connectsphere.likeservice.dto.ReactionCountResponse;
import com.connectsphere.likeservice.dto.ReactionSummaryResponse;
import com.connectsphere.likeservice.entity.Like;
import com.connectsphere.likeservice.service.LikeService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping({ "/api/likes", "/likes" })
@RequiredArgsConstructor
@Tag(name = "Reactions (Likes)", description = "Facebook-style reaction management for posts and comments")
public class LikeController {

	private final LikeService likeService;

	@Operation(
		summary = "React to a target",
		description = "Add or change a reaction on a POST or COMMENT. If the user already has a reaction it is replaced in-place. Accepted types: LIKE, LOVE, HAHA, WOW, SAD, ANGRY.",
		security = @SecurityRequirement(name = "bearerAuth")
	)
	@ApiResponses({
		@ApiResponse(responseCode = "201", description = "Reaction saved",
				content = @Content(schema = @Schema(implementation = Like.class))),
		@ApiResponse(responseCode = "400", description = "Missing or invalid parameters", content = @Content),
		@ApiResponse(responseCode = "401", description = "Not authenticated", content = @Content)
	})
	@PostMapping
	public ResponseEntity<Like> likeTarget(
			@AuthenticationPrincipal Integer userId,
			@Parameter(description = "ID of the post or comment") @RequestParam(required = false) Integer targetId,
			@Parameter(description = "Target type: POST or COMMENT") @RequestParam(required = false) String targetType,
			@Parameter(description = "Reaction type (default: LIKE)") @RequestParam(required = false) String reactionType,
			@RequestBody(required = false) LikeRequest request) {

		if (!isAuthenticated(userId)) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
		}

		Integer resolvedTargetId     = targetId != null ? targetId : (request != null ? request.getTargetId() : null);
		String  resolvedTargetType   = hasText(targetType) ? targetType : (request != null ? request.getTargetType() : null);
		String  resolvedReactionType = hasText(reactionType) ? reactionType
				: (request != null && hasText(request.getReactionType()) ? request.getReactionType() : "LIKE");

		if (resolvedTargetId == null || !hasText(resolvedTargetType)) {
			return ResponseEntity.badRequest().build();
		}

		Like like = likeService.likeTarget(userId, resolvedTargetId, resolvedTargetType, resolvedReactionType);
		return ResponseEntity.status(HttpStatus.CREATED).body(like);
	}

	@Operation(
		summary = "Remove a reaction",
		description = "Delete the authenticated user's reaction from a target.",
		security = @SecurityRequirement(name = "bearerAuth")
	)
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "Reaction removed"),
		@ApiResponse(responseCode = "401", description = "Not authenticated", content = @Content)
	})
	@DeleteMapping
	public ResponseEntity<Map<String, String>> unlikeTarget(
			@AuthenticationPrincipal Integer userId,
			@Parameter(description = "ID of the post or comment", required = true) @RequestParam int targetId,
			@Parameter(description = "Target type: POST or COMMENT", required = true) @RequestParam String targetType) {

		if (!isAuthenticated(userId)) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Authentication required"));
		}

		likeService.unlikeTarget(userId, targetId, targetType);
		return ResponseEntity.ok(Map.of("message", "Reaction removed"));
	}

	@Operation(
		summary = "Get total reaction count (legacy flat endpoint)",
		description = "Returns the total number of reactions on a target. Prefer /target/{targetType}/{targetId}/count."
	)
	@ApiResponse(responseCode = "200", description = "Total reaction count")
	@GetMapping("/count")
	public ResponseEntity<Long> getLikeCountLegacy(
			@Parameter(required = true) @RequestParam int targetId,
			@Parameter(required = true) @RequestParam String targetType) {
		return ResponseEntity.ok(likeService.getLikeCount(targetId, targetType));
	}

	@Operation(
		summary = "Get total reaction count for a target",
		description = "Returns {\"count\": N} — total reactions across all types."
	)
	@ApiResponse(responseCode = "200", description = "Total reaction count",
			content = @Content(schema = @Schema(example = "{\"count\":42}")))
	@GetMapping("/target/{targetType}/{targetId}/count")
	public ResponseEntity<Map<String, Long>> getLikeCount(
			@Parameter(description = "Target type: POST or COMMENT", required = true) @PathVariable String targetType,
			@Parameter(description = "ID of the post or comment", required = true) @PathVariable int targetId) {
		return ResponseEntity.ok(Map.of("count", likeService.getLikeCount(targetId, targetType)));
	}

	@Operation(
		summary = "Get count for a specific reaction type",
		description = "Returns the count of one reaction type (e.g. only LOVE) on a target."
	)
	@ApiResponse(responseCode = "200", description = "Reaction type count",
			content = @Content(schema = @Schema(implementation = ReactionCountResponse.class)))
	@GetMapping("/target/{targetType}/{targetId}/count-by-type")
	public ResponseEntity<ReactionCountResponse> getLikeCountByType(
			@Parameter(description = "Target type: POST or COMMENT", required = true) @PathVariable String targetType,
			@Parameter(description = "ID of the post or comment", required = true) @PathVariable int targetId,
			@Parameter(description = "Reaction type to count, e.g. LOVE", required = true) @RequestParam String reactionType) {
		Long count = likeService.getLikeCountByType(targetId, targetType, reactionType);
		return ResponseEntity.ok(new ReactionCountResponse(reactionType, count));
	}

	@Operation(
		summary = "Get full reaction summary for a target",
		description = "Facebook-style summary: totalCount, per-type breakdown (reactions map), and topReactions (up to 3 types for icon row display)."
	)
	@ApiResponse(responseCode = "200", description = "Reaction summary",
			content = @Content(schema = @Schema(implementation = ReactionSummaryResponse.class)))
	@GetMapping("/target/{targetType}/{targetId}/summary")
	public ResponseEntity<ReactionSummaryResponse> getReactionSummary(
			@Parameter(description = "Target type: POST or COMMENT", required = true) @PathVariable String targetType,
			@Parameter(description = "ID of the post or comment", required = true) @PathVariable int targetId) {
		return ResponseEntity.ok(likeService.getReactionSummary(targetId, targetType));
	}

	@Operation(
		summary = "Check if the current user has reacted (legacy flat endpoint)",
		description = "Returns true/false. Prefer /target/{targetType}/{targetId}/has-liked.",
		security = @SecurityRequirement(name = "bearerAuth")
	)
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "Whether the user has reacted"),
		@ApiResponse(responseCode = "401", description = "Not authenticated", content = @Content)
	})
	@GetMapping("/hasLiked")
	public ResponseEntity<Boolean> hasLikedLegacy(
			@AuthenticationPrincipal Integer userId,
			@Parameter(required = true) @RequestParam int targetId,
			@Parameter(required = true) @RequestParam String targetType) {
		if (!isAuthenticated(userId)) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
		}
		return ResponseEntity.ok(likeService.hasLiked(userId, targetId, targetType));
	}

	@Operation(
		summary = "Check if the current user has reacted",
		description = "Returns {\"hasLiked\": true/false}.",
		security = @SecurityRequirement(name = "bearerAuth")
	)
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "Reaction status",
				content = @Content(schema = @Schema(example = "{\"hasLiked\":true}"))),
		@ApiResponse(responseCode = "401", description = "Not authenticated", content = @Content)
	})
	@GetMapping("/target/{targetType}/{targetId}/has-liked")
	public ResponseEntity<Map<String, Boolean>> hasLiked(
			@AuthenticationPrincipal Integer userId,
			@Parameter(description = "Target type: POST or COMMENT", required = true) @PathVariable String targetType,
			@Parameter(description = "ID of the post or comment", required = true) @PathVariable int targetId) {
		if (!isAuthenticated(userId)) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
		}
		return ResponseEntity.ok(Map.of("hasLiked", likeService.hasLiked(userId, targetId, targetType)));
	}

	@Operation(
		summary = "Get the current user's own reaction",
		description = "Returns the Like entity (including reactionType and timestamp) for the authenticated user on the given target.",
		security = @SecurityRequirement(name = "bearerAuth")
	)
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "User's reaction",
				content = @Content(schema = @Schema(implementation = Like.class))),
		@ApiResponse(responseCode = "404", description = "User has not reacted", content = @Content),
		@ApiResponse(responseCode = "401", description = "Not authenticated", content = @Content)
	})
	@GetMapping("/target/{targetType}/{targetId}/me")
	public ResponseEntity<Like> getUserReaction(
			@AuthenticationPrincipal Integer userId,
			@Parameter(description = "Target type: POST or COMMENT", required = true) @PathVariable String targetType,
			@Parameter(description = "ID of the post or comment", required = true) @PathVariable int targetId) {
		if (!isAuthenticated(userId)) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
		}
		Optional<Like> reaction = likeService.getUserReaction(userId, targetId, targetType);
		return reaction.map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
	}

	@Operation(
		summary = "Switch the current user's reaction to a new type",
		description = "Changes the reaction type. The total counter is unchanged; only the type is updated.",
		security = @SecurityRequirement(name = "bearerAuth")
	)
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "Updated reaction",
				content = @Content(schema = @Schema(implementation = Like.class))),
		@ApiResponse(responseCode = "401", description = "Not authenticated", content = @Content)
	})
	@PutMapping
	public ResponseEntity<Like> changeReaction(
			@AuthenticationPrincipal Integer userId,
			@Parameter(required = true) @RequestParam int targetId,
			@Parameter(description = "Target type: POST or COMMENT", required = true) @RequestParam String targetType,
			@Parameter(description = "New reaction type, e.g. LOVE", required = true) @RequestParam String newReaction) {
		if (!isAuthenticated(userId)) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
		}
		Like updated = likeService.changeReaction(userId, targetId, targetType, newReaction);
		return ResponseEntity.ok(updated);
	}

	@Operation(
		summary = "List all reactions on a target",
		description = "Returns every Like entity for the given post or comment."
	)
	@ApiResponse(responseCode = "200", description = "List of reactions")
	@GetMapping("/target/{targetType}/{targetId}")
	public ResponseEntity<List<Like>> getLikesByTarget(
			@Parameter(description = "Target type: POST or COMMENT", required = true) @PathVariable String targetType,
			@Parameter(description = "ID of the post or comment", required = true) @PathVariable Integer targetId) {
		return ResponseEntity.ok(likeService.getLikesByTarget(targetId, targetType));
	}

	@Operation(
		summary = "List all reactions made by a user",
		description = "Returns every Like entity created by the specified user across all targets."
	)
	@ApiResponse(responseCode = "200", description = "List of reactions by the user")
	@GetMapping("/user/{userId}")
	public ResponseEntity<List<Like>> getLikesByUser(
			@Parameter(description = "User ID", required = true) @PathVariable int userId) {
		return ResponseEntity.ok(likeService.getLikesByUser(userId));
	}

	private boolean hasText(String value) {
		return value != null && !value.isBlank();
	}

	private boolean isAuthenticated(Integer userId) {
		return userId != null && userId > 0;
	}
}
