package com.connectsphere.post.controller;

import com.connectsphere.post.dto.ApiResponse;
import com.connectsphere.post.dto.MediaUrlsUpdateDTO;
import com.connectsphere.post.dto.PostRequestDTO;
import com.connectsphere.post.dto.PostResponseDTO;
import com.connectsphere.post.entity.Visibility;
import com.connectsphere.post.exception.UnauthorizedAccessException;
import com.connectsphere.post.service.PostService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/posts")
@Tag(name = "Posts", description = "CRUD, feed, engagement, and search operations for posts")
public class PostController {

    private final PostService postService;

    public PostController(PostService postService) {
        this.postService = postService;
    }

    // ── Auth helper ───────────────────────────────────────────────────────────

    private Long getAuthenticatedUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() != null) {
            try {
                return Long.valueOf(auth.getPrincipal().toString());
            } catch (NumberFormatException ignored) { }
        }
        return null;
    }

    private Long requireAuthenticatedUserId() {
        Long id = getAuthenticatedUserId();
        if (id == null) throw new UnauthorizedAccessException("User not authenticated");
        return id;
    }

    // ── CRUD ──────────────────────────────────────────────────────────────────

    @PostMapping
    @Operation(
            summary = "Create a new post",
            description = "Creates a post for the authenticated user. " +
                    "Content moderation is triggered asynchronously (< 30 s).",
            security = @SecurityRequirement(name = "BearerAuth")
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201",
                    description = "Post created",
                    content = @Content(schema = @Schema(implementation = PostResponseDTO.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                    description = "Validation error", content = @Content),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                    description = "Unauthorized", content = @Content)
    })
    public ResponseEntity<PostResponseDTO> createPost(@Valid @RequestBody PostRequestDTO requestDTO) {
        Long authorId = requireAuthenticatedUserId();
        PostResponseDTO created = postService.createPost(requestDTO, authorId);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/{postId}")
    @Operation(summary = "Get a single post by ID")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Post found"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                    description = "Post not found", content = @Content)
    })
    public ResponseEntity<PostResponseDTO> getPostById(
            @Parameter(description = "Post ID", required = true) @PathVariable Long postId) {
        return ResponseEntity.ok(postService.getPostById(postId));
    }

    @PutMapping("/{postId}")
    @Operation(
            summary = "Update a post",
            description = "Only the original author may update their post.",
            security = @SecurityRequirement(name = "BearerAuth")
    )
    public ResponseEntity<PostResponseDTO> updatePost(
            @PathVariable Long postId,
            @Valid @RequestBody PostRequestDTO requestDTO) {
        Long authorId = requireAuthenticatedUserId();
        return ResponseEntity.ok(postService.updatePost(postId, requestDTO, authorId));
    }

    @DeleteMapping("/{postId}")
    @Operation(
            summary = "Delete a post",
            description = "Soft-deletes the post. Only the author can delete their own post.",
            security = @SecurityRequirement(name = "BearerAuth")
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "Deleted"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403",
                    description = "Not the author", content = @Content)
    })
    public ResponseEntity<Void> deletePost(@PathVariable Long postId) {
        Long authorId = requireAuthenticatedUserId();
        postService.deletePost(postId, authorId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    @Operation(summary = "List all posts", description = "Returns all non-deleted posts. Primarily used internally.")
    public ResponseEntity<List<PostResponseDTO>> getAllPosts() {
        return ResponseEntity.ok(postService.getAllPosts());
    }

    // ── Feed ──────────────────────────────────────────────────────────────────

    @GetMapping("/feed/{userId}")
    @Operation(
            summary = "Get personalised feed for a user",
            description = """
                    Returns the personalised news feed for the given user.
                    Feed is served from Redis cache (pre-computed every 5 minutes)
                    to meet the 1.5-second SLA for 50,000 concurrent users.
                    On a cache miss the feed is computed on-the-fly and then cached.
                    """,
            security = @SecurityRequirement(name = "BearerAuth")
    )
    public ResponseEntity<List<PostResponseDTO>> getFeed(
            @Parameter(description = "User whose feed to retrieve", required = true)
            @PathVariable Long userId) {
        Long authenticatedUserId = requireAuthenticatedUserId();
        if (!authenticatedUserId.equals(userId)) {
            throw new UnauthorizedAccessException("You can only access your own feed");
        }
        return ResponseEntity.ok(postService.getFeedByUserId(userId));
    }

    @PostMapping("/feed")
    @Operation(
            summary = "Generate feed from a list of followee IDs",
            description = "Internal / service-to-service endpoint. Computes feed from an explicit list of author IDs."
    )
    public ResponseEntity<List<PostResponseDTO>> generateFeed(@RequestBody List<Long> followeeIds) {
        return ResponseEntity.ok(postService.generateFeed(followeeIds));
    }

    // ── User posts ────────────────────────────────────────────────────────────

    @GetMapping("/user/{userId}")
    @Operation(summary = "Get posts by user", description = "Returns all posts for a given user (for profile pages).")
    public ResponseEntity<List<PostResponseDTO>> getPostsByUser(@PathVariable Long userId) {
        return ResponseEntity.ok(postService.getPostsByUser(userId));
    }

    // ── Search ────────────────────────────────────────────────────────────────

    @GetMapping("/search")
    @Operation(summary = "Search posts by keyword", description = "Full-text keyword search across post content.")
    public ResponseEntity<List<PostResponseDTO>> searchPosts(
            @Parameter(description = "Search keyword", required = true)
            @RequestParam String keyword) {
        return ResponseEntity.ok(postService.searchPosts(keyword));
    }

    // ── Visibility ────────────────────────────────────────────────────────────

    @PutMapping("/{postId}/visibility")
    @Operation(
            summary = "Change post visibility",
            security = @SecurityRequirement(name = "BearerAuth")
    )
    public ResponseEntity<PostResponseDTO> changeVisibility(
            @PathVariable Long postId,
            @Parameter(description = "New visibility value", required = true)
            @RequestParam Visibility visibility) {
        Long authorId = requireAuthenticatedUserId();
        return ResponseEntity.ok(postService.changeVisibility(postId, visibility, authorId));
    }

    // ── Media ─────────────────────────────────────────────────────────────────

    @PutMapping("/{postId}/mediaUrls")
    @Operation(
            summary = "Append media URLs to a post",
            description = "Called by the media-service after upload is complete."
    )
    public ResponseEntity<PostResponseDTO> updateMediaUrls(
            @PathVariable Long postId,
            @RequestBody MediaUrlsUpdateDTO dto) {
        return ResponseEntity.ok(postService.updateMediaUrls(postId, dto.getMediaUrls()));
    }

    // ── Engagement ────────────────────────────────────────────────────────────

    @PutMapping("/{postId}/like/increment")
    @Operation(summary = "Increment like count", description = "Called by the like-service.")
    public ResponseEntity<PostResponseDTO> incrementLike(@PathVariable Long postId) {
        return ResponseEntity.ok(postService.incrementLike(postId));
    }

    @PutMapping("/{postId}/like/decrement")
    @Operation(summary = "Decrement like count", description = "Called by the like-service.")
    public ResponseEntity<PostResponseDTO> decrementLike(@PathVariable Long postId) {
        return ResponseEntity.ok(postService.decrementLike(postId));
    }

    @PutMapping("/{postId}/comment/increment")
    @Operation(summary = "Increment comment count", description = "Called by the comment-service.")
    public ResponseEntity<PostResponseDTO> incrementComment(@PathVariable Long postId) {
        return ResponseEntity.ok(postService.incrementComment(postId));
    }

    @PutMapping("/{postId}/comment/decrement")
    @Operation(summary = "Decrement comment count", description = "Called by the comment-service.")
    public ResponseEntity<PostResponseDTO> decrementComment(@PathVariable Long postId) {
        return ResponseEntity.ok(postService.decrementComment(postId));
    }

    // ── Health ────────────────────────────────────────────────────────────────

    @GetMapping("/test")
    @Operation(summary = "Health check", description = "Returns a plain-text confirmation the service is running.")
    public ResponseEntity<String> test() {
        return ResponseEntity.ok("POST SERVICE WORKING");
    }
}
