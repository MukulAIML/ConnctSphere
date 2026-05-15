package com.connectsphere.commentservice.controller;

import com.connectsphere.commentservice.dto.AdminDeleteCommentResponse;
import com.connectsphere.commentservice.service.AdminModerationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/admin/comments")
@RequiredArgsConstructor
@Tag(name = "Admin Moderation", description = "Administrative endpoints for content moderation. Require ROLE_ADMIN or ROLE_MODERATOR JWT.")
@SecurityRequirement(name = "bearerAuth")
public class AdminCommentController {

    private final AdminModerationService adminModerationService;

    // -----------------------------------------------------------------------
    // Force-delete a single comment
    // -----------------------------------------------------------------------

    @Operation(
            summary = "Force-delete a comment",
            description = """
                    Forcefully removes a comment regardless of ownership.
                    If the target is a top-level comment, all its replies are cascade-deleted as well.
                    Requires `ROLE_ADMIN` or `ROLE_MODERATOR` in the JWT.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Comment (and any replies) successfully removed.",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = AdminDeleteCommentResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid JWT.",
                    content = @Content(mediaType = "application/json")),
            @ApiResponse(responseCode = "403", description = "Authenticated user does not have moderation privileges.",
                    content = @Content(mediaType = "application/json")),
            @ApiResponse(responseCode = "404", description = "Comment not found or already deleted.",
                    content = @Content(mediaType = "application/json"))
    })
    @DeleteMapping("/{commentId}")
    public ResponseEntity<?> forceDeleteComment(
            @AuthenticationPrincipal Integer adminId,
            @Parameter(description = "ID of the comment to remove.", required = true, example = "42")
            @PathVariable int commentId,
            @Parameter(description = "Moderation reason (stored in audit log).", example = "Hate speech")
            @RequestParam(required = false) String reason) {

        if (adminId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Authentication required"));
        }
        if (!hasModerationRole()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Moderator/Admin role required for this operation"));
        }

        AdminDeleteCommentResponse result =
                adminModerationService.forceDeleteComment(commentId, adminId, reason);
        return ResponseEntity.ok(result);
    }

    // -----------------------------------------------------------------------
    // Purge all comments by a user
    // -----------------------------------------------------------------------

    @Operation(
            summary = "Purge all comments by a user",
            description = """
                    Forcefully removes every non-deleted comment (and their replies) authored by the
                    specified user. Typically called after a user ban.
                    Requires `ROLE_ADMIN` or `ROLE_MODERATOR` in the JWT.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200",
                    description = "All comments by the user removed. Returns a list of deletion summaries.",
                    content = @Content(mediaType = "application/json",
                            array = @ArraySchema(schema = @Schema(implementation = AdminDeleteCommentResponse.class)))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid JWT.",
                    content = @Content(mediaType = "application/json")),
            @ApiResponse(responseCode = "403", description = "Authenticated user does not have moderation privileges.",
                    content = @Content(mediaType = "application/json"))
    })
    @DeleteMapping("/user/{userId}")
    public ResponseEntity<?> forceDeleteAllCommentsByUser(
            @AuthenticationPrincipal Integer adminId,
            @Parameter(description = "ID of the user whose comments should be purged.", required = true, example = "101")
            @PathVariable int userId,
            @Parameter(description = "Moderation reason.", example = "User permanently banned")
            @RequestParam(required = false) String reason) {

        if (adminId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Authentication required"));
        }
        if (!hasModerationRole()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Moderator/Admin role required for this operation"));
        }

        List<AdminDeleteCommentResponse> results =
                adminModerationService.forceDeleteAllCommentsByUser(userId, adminId, reason);
        return ResponseEntity.ok(Map.of(
                "purgedCount", results.size(),
                "targetUserId", userId,
                "details", results));
    }

    // -----------------------------------------------------------------------
    // Purge all comments on a post
    // -----------------------------------------------------------------------

    @Operation(
            summary = "Purge all comments on a post",
            description = """
                    Forcefully removes every non-deleted top-level comment (and their replies) on a
                    given post. Typically called when the post itself is being removed or taken down.
                    Requires `ROLE_ADMIN` or `ROLE_MODERATOR` in the JWT.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200",
                    description = "All comments on the post removed. Returns a list of deletion summaries.",
                    content = @Content(mediaType = "application/json",
                            array = @ArraySchema(schema = @Schema(implementation = AdminDeleteCommentResponse.class)))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid JWT.",
                    content = @Content(mediaType = "application/json")),
            @ApiResponse(responseCode = "403", description = "Authenticated user does not have moderation privileges.",
                    content = @Content(mediaType = "application/json"))
    })
    @DeleteMapping("/post/{postId}")
    public ResponseEntity<?> forceDeleteAllCommentsByPost(
            @AuthenticationPrincipal Integer adminId,
            @Parameter(description = "ID of the post whose comments should be purged.", required = true, example = "7")
            @PathVariable int postId,
            @Parameter(description = "Moderation reason.", example = "Post taken down for policy violation")
            @RequestParam(required = false) String reason) {

        if (adminId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Authentication required"));
        }
        if (!hasModerationRole()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Moderator/Admin role required for this operation"));
        }

        List<AdminDeleteCommentResponse> results =
                adminModerationService.forceDeleteAllCommentsByPost(postId, adminId, reason);
        return ResponseEntity.ok(Map.of(
                "purgedCount", results.size(),
                "postId", postId,
                "details", results));
    }

    private boolean hasModerationRole() {
        return SecurityContextHolder.getContext().getAuthentication() != null
                && SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                .map(authority -> authority == null ? null : authority.getAuthority())
                .anyMatch(role -> "ROLE_ADMIN".equals(role) || "ROLE_MODERATOR".equals(role));
    }
}
