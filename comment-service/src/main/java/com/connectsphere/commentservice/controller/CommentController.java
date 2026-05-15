package com.connectsphere.commentservice.controller;

import java.util.List;
import java.util.Map;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
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
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.connectsphere.commentservice.dto.CreateCommentRequest;
import com.connectsphere.commentservice.dto.UpdateCommentRequest;
import com.connectsphere.commentservice.entity.Comment;
import com.connectsphere.commentservice.service.CommentService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/comments")
@RequiredArgsConstructor
@Tag(name = "Comments", description = "CRUD operations and interactions for post comments.")
public class CommentController {

	private final CommentService commentService;

	// -----------------------------------------------------------------------
	// Create
	// -----------------------------------------------------------------------

	@Operation(
			summary = "Create a comment",
			description = "Creates a top-level comment on a post. " +
					"If `parentCommentId` is set in the body the request is treated as a reply.",
			security = @SecurityRequirement(name = "bearerAuth"))
	@ApiResponses({
			@ApiResponse(responseCode = "201", description = "Comment created.",
					content = @Content(mediaType = "application/json", schema = @Schema(implementation = Comment.class))),
			@ApiResponse(responseCode = "400", description = "Validation error or business-rule violation.",
					content = @Content(mediaType = "application/json")),
			@ApiResponse(responseCode = "401", description = "Unauthenticated.",
					content = @Content(mediaType = "application/json"))
	})
	@PostMapping
	public ResponseEntity<Comment> addComment(
			@AuthenticationPrincipal Integer userId,
			@Valid @RequestBody CreateCommentRequest request) {

		if (!isAuthenticated(userId)) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
		}

		request.setAuthorId(userId);
		Comment created = request.getParentCommentId() == null
				? commentService.addComment(request)
				: commentService.addReply(request.getParentCommentId(), request);
		return ResponseEntity.status(HttpStatus.CREATED).body(created);
	}

	@Operation(
			summary = "Reply to a comment",
			description = "Creates a reply to the specified comment. Only one level of nesting is supported.",
			security = @SecurityRequirement(name = "bearerAuth"))
	@ApiResponses({
			@ApiResponse(responseCode = "201", description = "Reply created.",
					content = @Content(mediaType = "application/json", schema = @Schema(implementation = Comment.class))),
			@ApiResponse(responseCode = "400", description = "Validation error or nesting violation.",
					content = @Content(mediaType = "application/json")),
			@ApiResponse(responseCode = "401", description = "Unauthenticated.",
					content = @Content(mediaType = "application/json")),
			@ApiResponse(responseCode = "404", description = "Parent comment not found.",
					content = @Content(mediaType = "application/json"))
	})
	@PostMapping("/{commentId}/replies")
	public ResponseEntity<Comment> addReply(
			@AuthenticationPrincipal Integer userId,
			@Parameter(description = "ID of the parent comment.", required = true, example = "10")
			@PathVariable int commentId,
			@Valid @RequestBody CreateCommentRequest request) {

		if (!isAuthenticated(userId)) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
		}

		request.setAuthorId(userId);
		Comment reply = commentService.addReply(commentId, request);
		return ResponseEntity.status(HttpStatus.CREATED).body(reply);
	}

	// -----------------------------------------------------------------------
	// Read
	// -----------------------------------------------------------------------

	@Operation(
			summary = "Get comments for a post (path variable)",
			description = "Returns all top-level comments for a post, each with their replies embedded.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "List of comments.",
					content = @Content(mediaType = "application/json",
							array = @ArraySchema(schema = @Schema(implementation = Comment.class))))
	})
	@GetMapping("/post/{postId}")
	public ResponseEntity<List<Comment>> getCommentsByPost(
			@Parameter(description = "Post ID.", required = true, example = "5")
			@PathVariable int postId) {
		return ResponseEntity.ok(commentService.getCommentsByPost(postId));
	}

	@Operation(
			summary = "Get comments for a post (query parameter)",
			description = "Alternate route that accepts the post ID as a query param.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "List of comments.",
					content = @Content(mediaType = "application/json",
							array = @ArraySchema(schema = @Schema(implementation = Comment.class))))
	})
	@GetMapping(params = "postId")
	public ResponseEntity<List<Comment>> getCommentsByPostQuery(
			@Parameter(description = "Post ID.", required = true, example = "5")
			@RequestParam int postId) {
		return ResponseEntity.ok(commentService.getCommentsByPost(postId));
	}

	@Operation(summary = "Get a comment by ID")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "The comment.",
					content = @Content(mediaType = "application/json", schema = @Schema(implementation = Comment.class))),
			@ApiResponse(responseCode = "404", description = "Comment not found.",
					content = @Content(mediaType = "application/json"))
	})
	@GetMapping("/{commentId}")
	public ResponseEntity<Comment> getCommentById(
			@Parameter(description = "Comment ID.", required = true, example = "42")
			@PathVariable int commentId) {
		return ResponseEntity.ok(commentService.getCommentById(commentId));
	}

	@Operation(summary = "Get replies for a comment")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "List of reply comments.",
					content = @Content(mediaType = "application/json",
							array = @ArraySchema(schema = @Schema(implementation = Comment.class)))),
			@ApiResponse(responseCode = "404", description = "Parent comment not found.",
					content = @Content(mediaType = "application/json"))
	})
	@GetMapping("/{commentId}/replies")
	public ResponseEntity<List<Comment>> getReplies(
			@Parameter(description = "Parent comment ID.", required = true, example = "10")
			@PathVariable int commentId) {
		return ResponseEntity.ok(commentService.getReplies(commentId));
	}

	@Operation(summary = "Get comments authored by a user")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "List of comments by the user.",
					content = @Content(mediaType = "application/json",
							array = @ArraySchema(schema = @Schema(implementation = Comment.class))))
	})
	@GetMapping("/user/{userId}")
	public ResponseEntity<List<Comment>> getCommentsByUser(
			@Parameter(description = "User ID.", required = true, example = "101")
			@PathVariable int userId) {
		return ResponseEntity.ok(commentService.getCommentsByUser(userId));
	}

	@Operation(summary = "Get total comment count for a post")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Comment count.",
					content = @Content(mediaType = "application/json",
							schema = @Schema(example = "{\"commentCount\": 7}")))
	})
	@GetMapping("/post/{postId}/count")
	public ResponseEntity<Map<String, Integer>> getCommentCount(
			@Parameter(description = "Post ID.", required = true, example = "5")
			@PathVariable int postId) {
		return ResponseEntity.ok(Map.of("commentCount", commentService.getCommentCount(postId)));
	}

	// -----------------------------------------------------------------------
	// Update
	// -----------------------------------------------------------------------

	@Operation(
			summary = "Update a comment",
			description = "Updates the content of a comment. Only the original author may update their comment.",
			security = @SecurityRequirement(name = "bearerAuth"))
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Updated comment.",
					content = @Content(mediaType = "application/json", schema = @Schema(implementation = Comment.class))),
			@ApiResponse(responseCode = "400", description = "Validation error.",
					content = @Content(mediaType = "application/json")),
			@ApiResponse(responseCode = "401", description = "Unauthenticated.",
					content = @Content(mediaType = "application/json")),
			@ApiResponse(responseCode = "403", description = "Not the comment owner.",
					content = @Content(mediaType = "application/json")),
			@ApiResponse(responseCode = "404", description = "Comment not found.",
					content = @Content(mediaType = "application/json"))
	})
	@PutMapping("/{commentId}")
	public ResponseEntity<Comment> updateComment(
			@AuthenticationPrincipal Integer userId,
			@Parameter(description = "Comment ID.", required = true, example = "42")
			@PathVariable int commentId,
			@Valid @RequestBody UpdateCommentRequest request) {

		if (!isAuthenticated(userId)) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
		}

		Comment existing = commentService.getCommentById(commentId);
		if (existing.getAuthorId() != userId) {
			return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
		}
		Comment updated = commentService.updateComment(commentId, request.getContent());
		return ResponseEntity.ok(updated);
	}

	// -----------------------------------------------------------------------
	// Delete
	// -----------------------------------------------------------------------

	@Operation(
			summary = "Delete a comment",
			description = "Soft-deletes a comment. The owner may delete their own comment; " +
					"a user with `ROLE_ADMIN` (via `X-User-Role` header) may delete any comment. " +
					"For admin-initiated bulk deletions see the `/admin/comments` endpoints.",
			security = @SecurityRequirement(name = "bearerAuth"))
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Comment deleted.",
					content = @Content(mediaType = "application/json",
							schema = @Schema(example = "{\"message\": \"Comment deleted successfully\"}"))),
			@ApiResponse(responseCode = "401", description = "Unauthenticated.",
					content = @Content(mediaType = "application/json")),
			@ApiResponse(responseCode = "403", description = "Not the owner and not an admin.",
					content = @Content(mediaType = "application/json")),
			@ApiResponse(responseCode = "404", description = "Comment not found.",
					content = @Content(mediaType = "application/json"))
	})
	@DeleteMapping("/{commentId}")
	public ResponseEntity<Map<String, String>> deleteComment(
			@AuthenticationPrincipal Integer userId,
			@Parameter(description = "Role of the authenticated user.", example = "ROLE_ADMIN")
			@RequestHeader(value = "X-User-Role", defaultValue = "ROLE_USER") String userRole,
			@Parameter(description = "Comment ID.", required = true, example = "42")
			@PathVariable int commentId) {

		if (!isAuthenticated(userId)) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
					.body(Map.of("error", "Authentication required"));
		}

		Comment existing = commentService.getCommentById(commentId);
		boolean isAdmin = "ROLE_ADMIN".equals(userRole);
		boolean isOwner = existing.getAuthorId() == userId;

		if (!isAdmin && !isOwner) {
			return ResponseEntity.status(HttpStatus.FORBIDDEN)
					.body(Map.of("error", "You may only delete your own comments"));
		}

		commentService.deleteComment(commentId);
		return ResponseEntity.ok(Map.of("message", "Comment deleted successfully"));
	}

	// -----------------------------------------------------------------------
	// Likes
	// -----------------------------------------------------------------------

	@Operation(
			summary = "Like (toggle) a comment",
			description = "Toggles a like on the given comment for the authenticated user. " +
					"If the user has already liked it the like is removed (idempotent toggle).")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Comment with updated like count.",
					content = @Content(mediaType = "application/json", schema = @Schema(implementation = Comment.class))),
			@ApiResponse(responseCode = "404", description = "Comment not found.",
					content = @Content(mediaType = "application/json"))
	})
	@PostMapping("/{commentId}/like")
	public ResponseEntity<Comment> likeComment(
			@AuthenticationPrincipal Integer userId,
			@Parameter(description = "Comment ID.", required = true, example = "42")
			@PathVariable int commentId) {
		if (!isAuthenticated(userId)) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
		}
		return ResponseEntity.ok(commentService.likeComment(commentId, userId));
	}

	@Operation(
			summary = "Unlike a comment (DELETE)",
			description = "Removes the authenticated user's like from a comment.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Comment with updated like count.",
					content = @Content(mediaType = "application/json", schema = @Schema(implementation = Comment.class))),
			@ApiResponse(responseCode = "404", description = "Comment not found.",
					content = @Content(mediaType = "application/json"))
	})
	@DeleteMapping("/{commentId}/like")
	public ResponseEntity<Comment> unlikeComment(
			@AuthenticationPrincipal Integer userId,
			@Parameter(description = "Comment ID.", required = true, example = "42")
			@PathVariable int commentId) {
		if (!isAuthenticated(userId)) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
		}
		return ResponseEntity.ok(commentService.unlikeComment(commentId, userId));
	}

	@Operation(
			summary = "Unlike a comment (POST — legacy)",
			description = "Legacy endpoint kept for backwards compatibility. Prefer `DELETE /{commentId}/like`.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Comment with updated like count.",
					content = @Content(mediaType = "application/json", schema = @Schema(implementation = Comment.class))),
			@ApiResponse(responseCode = "404", description = "Comment not found.",
					content = @Content(mediaType = "application/json"))
	})
	@PostMapping("/{commentId}/unlike")
	public ResponseEntity<Comment> unlikeCommentLegacy(
			@AuthenticationPrincipal Integer userId,
			@Parameter(description = "Comment ID.", required = true, example = "42")
			@PathVariable int commentId) {
		if (!isAuthenticated(userId)) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
		}
		return ResponseEntity.ok(commentService.unlikeComment(commentId, userId));
	}

	@Operation(
			summary = "Increment comment like counter (internal)",
			description = "Internal endpoint used by like-service to increment aggregate comment reactions.")
	@PutMapping("/{commentId}/like/increment")
	public ResponseEntity<Comment> incrementLikeCountInternal(
			@Parameter(description = "Comment ID.", required = true, example = "42")
			@PathVariable int commentId) {
		return ResponseEntity.ok(commentService.likeComment(commentId, null));
	}

	@Operation(
			summary = "Decrement comment like counter (internal)",
			description = "Internal endpoint used by like-service to decrement aggregate comment reactions.")
	@PutMapping("/{commentId}/like/decrement")
	public ResponseEntity<Comment> decrementLikeCountInternal(
			@Parameter(description = "Comment ID.", required = true, example = "42")
			@PathVariable int commentId) {
		return ResponseEntity.ok(commentService.unlikeComment(commentId, null));
	}

	private boolean isAuthenticated(Integer userId) {
		return userId != null && userId > 0;
	}
}
