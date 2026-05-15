package com.connectsphere.media.controller;

import com.connectsphere.media.dto.ApiResponse;
import com.connectsphere.media.dto.StoryRequestDTO;
import com.connectsphere.media.dto.StoryResponseDTO;
import com.connectsphere.media.service.StoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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

@Tag(name = "Stories", description = "24-hour ephemeral stories – create, view and delete.")
@RestController
@RequestMapping("/stories")
@SecurityRequirement(name = "bearerAuth")
public class StoryController {

    private static final Logger logger = LoggerFactory.getLogger(StoryController.class);

    private final StoryService storyService;

    public StoryController(StoryService storyService) {
        this.storyService = storyService;
    }

    // ------------------------------------------------------------------
    // POST /stories
    // ------------------------------------------------------------------

    @Operation(summary = "Create a new story",
            description = "Creates a 24-hour ephemeral story. The `mediaUrl` must already be a "
                    + "publicly accessible URL uploaded by the authenticated user (typically a CloudFront "
                    + "CDN URL obtained after calling `POST /media/upload`).")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Story created"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation error"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthenticated")
    })
    @PostMapping
    public ResponseEntity<ApiResponse<StoryResponseDTO>> createStory(
            @Valid @RequestBody StoryRequestDTO requestDTO) {

        Long authorId = getAuthenticatedUserId();
        if (authorId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error(HttpStatus.UNAUTHORIZED.value(), "User not authenticated"));
        }
        logger.info("Received request to create story by user: {}", authorId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created("Story created successfully",
                        storyService.createStory(requestDTO, authorId)));
    }

    // ------------------------------------------------------------------
    // GET /stories/active
    // ------------------------------------------------------------------

    @Operation(summary = "Get all active stories",
            description = "Returns all stories that have not yet expired (i.e. within 24 hours of creation), "
                    + "ordered newest-first.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "List of active stories"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthenticated")
    })
    @GetMapping("/active")
    public ResponseEntity<ApiResponse<List<StoryResponseDTO>>> getActiveStories() {
        logger.info("Received request to fetch active stories");
        return ResponseEntity.ok(
                ApiResponse.success("Active stories fetched successfully",
                        storyService.getActiveStories()));
    }

    // ------------------------------------------------------------------
    // GET /stories/user/{userId}
    // ------------------------------------------------------------------

    @Operation(summary = "Get active stories by user",
            description = "Returns all active stories authored by the given user, newest-first.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "List of user's active stories"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthenticated")
    })
    @GetMapping("/user/{userId}")
    public ResponseEntity<ApiResponse<List<StoryResponseDTO>>> getStoriesByUser(
            @Parameter(description = "Author's user ID", required = true)
            @PathVariable("userId") Long userId) {

        logger.info("Received request to fetch stories for user: {}", userId);
        return ResponseEntity.ok(
                ApiResponse.success("User stories fetched successfully",
                        storyService.getStoriesByUser(userId)));
    }

    // ------------------------------------------------------------------
    // PUT|POST /stories/{id}/view
    // ------------------------------------------------------------------

    @Operation(summary = "Mark a story as viewed",
            description = "Increments the view count for the story. "
                    + "Viewing your own story does not increment the counter.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Story viewed"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Story not found or expired"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthenticated")
    })
    @RequestMapping(value = "/{id}/view", method = {RequestMethod.PUT, RequestMethod.POST})
    public ResponseEntity<ApiResponse<Void>> viewStory(
            @Parameter(description = "Story ID", required = true)
            @PathVariable("id") Long id) {

        Long viewerId = getAuthenticatedUserId();
        logger.info("Received request to view story: {}", id);
        storyService.viewStory(id, viewerId);
        return ResponseEntity.ok(ApiResponse.success("Story viewed successfully", null));
    }

    // ------------------------------------------------------------------
    // DELETE /stories/{id}
    // ------------------------------------------------------------------

    @Operation(summary = "Delete a story",
            description = "Permanently removes the story. Only the author may delete.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Story deleted"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthenticated"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden – not your story"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Story not found")
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteStory(
            @Parameter(description = "Story ID", required = true)
            @PathVariable("id") Long id) {

        Long authorId = getAuthenticatedUserId();
        if (authorId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error(HttpStatus.UNAUTHORIZED.value(), "User not authenticated"));
        }
        logger.info("Received request to delete story {} by user: {}", id, authorId);
        storyService.deleteStory(id, authorId);
        return ResponseEntity.ok(ApiResponse.success("Story deleted successfully", null));
    }

    // ------------------------------------------------------------------

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
}
