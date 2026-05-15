package com.connectsphere.post.controller;

import com.connectsphere.post.dto.AdminEditRequestDTO;
import com.connectsphere.post.dto.PostResponseDTO;
import com.connectsphere.post.service.PostService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Admin Moderation Controller.
 *
 * <p>All endpoints under {@code /admin/posts/**} are intended for privileged
 * internal use only. In production, access should be restricted at the API
 * Gateway level (e.g., via an ADMIN role claim in the JWT or IP allowlist).
 *
 * <p>These endpoints allow administrators to:
 * <ul>
 *   <li>List posts flagged by the AI moderation pipeline</li>
 *   <li>Force-edit inappropriate post content</li>
 *   <li>Force-delete policy-violating posts</li>
 *   <li>Mark flagged posts as reviewed (no action needed)</li>
 * </ul>
 */
@RestController
@RequestMapping({"/admin/posts", "/posts/admin/posts"})
@Tag(name = "Admin Moderation", description = "Privileged endpoints for human review of AI-flagged posts")
@SecurityRequirement(name = "BearerAuth")
public class AdminPostController {

    private final PostService postService;

    public AdminPostController(PostService postService) {
        this.postService = postService;
    }

    // ── GET flagged posts ─────────────────────────────────────────────────────

    @GetMapping("/flagged")
    @Operation(
            summary = "List AI-flagged posts",
            description = "Returns all posts flagged by the AWS Rekognition moderation pipeline that are pending human review."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Flagged posts returned successfully"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid JWT", content = @Content),
            @ApiResponse(responseCode = "403", description = "Insufficient privileges",  content = @Content)
    })
    public ResponseEntity<List<PostResponseDTO>> getFlaggedPosts() {
        return ResponseEntity.ok(postService.getFlaggedPosts());
    }

    // ── Force-edit ────────────────────────────────────────────────────────────

    @PutMapping("/{postId}/force-edit")
    @Operation(
            summary = "Force-edit a post",
            description = """
                    Overrides the content of any post regardless of authorship.
                    The original author is NOT notified by this service — notifications
                    should be triggered by a downstream event/notification service.
                    The post is re-indexed in the search service and the author's
                    Redis feed cache is evicted automatically.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Post content replaced successfully",
                    content = @Content(schema = @Schema(implementation = PostResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "Post not found",  content = @Content),
            @ApiResponse(responseCode = "401", description = "Unauthorized",    content = @Content)
    })
    public ResponseEntity<PostResponseDTO> forceEditPost(
            @Parameter(description = "ID of the post to edit", required = true)
            @PathVariable Long postId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Replacement content and admin note",
                    required = true,
                    content = @Content(schema = @Schema(implementation = AdminEditRequestDTO.class))
            )
            @RequestBody AdminEditRequestDTO request) {

        PostResponseDTO result = postService.adminForceEditPost(
                postId,
                request.getContent(),
                request.getAdminNote()
        );
        return ResponseEntity.ok(result);
    }

    // ── Force-delete ──────────────────────────────────────────────────────────

    @DeleteMapping("/{postId}/force-delete")
    @Operation(
            summary = "Force-delete a post",
            description = """
                    Soft-deletes any post regardless of authorship.
                    Triggers search de-indexing and media soft-delete.
                    The post is hidden from all feeds immediately (cache evicted).
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Post removed successfully"),
            @ApiResponse(responseCode = "404", description = "Post not found", content = @Content),
            @ApiResponse(responseCode = "401", description = "Unauthorized",   content = @Content)
    })
    public ResponseEntity<Map<String, String>> forceDeletePost(
            @Parameter(description = "ID of the post to remove", required = true)
            @PathVariable Long postId,
            @Parameter(description = "Admin note explaining the removal reason")
            @RequestParam(required = false, defaultValue = "Removed for policy violation") String adminNote) {

        postService.adminForceDeletePost(postId, adminNote);
        return ResponseEntity.ok(Map.of(
                "message", "Post " + postId + " has been forcefully removed.",
                "adminNote", adminNote
        ));
    }

    // ── Mark reviewed ─────────────────────────────────────────────────────────

    @PatchMapping("/{postId}/mark-reviewed")
    @Operation(
            summary = "Mark a flagged post as reviewed",
            description = """
                    Clears the isFlagged flag and marks moderationReviewed = true.
                    Use this when the AI flagged a false-positive and no content
                    action is needed. Optionally attach an admin note.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Post marked as reviewed",
                    content = @Content(schema = @Schema(implementation = PostResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "Post not found", content = @Content),
            @ApiResponse(responseCode = "401", description = "Unauthorized",   content = @Content)
    })
    public ResponseEntity<PostResponseDTO> markReviewed(
            @Parameter(description = "ID of the post to mark as reviewed", required = true)
            @PathVariable Long postId,
            @Parameter(description = "Optional admin note")
            @RequestParam(required = false) String adminNote) {

        return ResponseEntity.ok(postService.adminMarkReviewed(postId, adminNote));
    }
}
