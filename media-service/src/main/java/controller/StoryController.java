package com.connectsphere.media.controller;

import com.connectsphere.media.dto.ApiResponse;
import com.connectsphere.media.dto.StoryRequestDTO;
import com.connectsphere.media.dto.StoryResponseDTO;
import com.connectsphere.media.service.StoryService;
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
@RequestMapping("/stories")
public class StoryController {

    private static final Logger logger = LoggerFactory.getLogger(StoryController.class);

    private final StoryService storyService;

    public StoryController(StoryService storyService) {
        this.storyService = storyService;
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
    public ResponseEntity<ApiResponse<StoryResponseDTO>> createStory(@Valid @RequestBody StoryRequestDTO requestDTO) {
        Long authorId = getAuthenticatedUserId();
        if (authorId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error(HttpStatus.UNAUTHORIZED.value(), "User not authenticated"));
        }
        logger.info("Received request to create story by user: {}", authorId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created("Story created successfully", storyService.createStory(requestDTO, authorId)));
    }

    @GetMapping("/active")
    public ResponseEntity<ApiResponse<List<StoryResponseDTO>>> getActiveStories() {
        logger.info("Received request to fetch active stories");
        return ResponseEntity.ok(ApiResponse.success("Active stories fetched successfully", storyService.getActiveStories()));
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<ApiResponse<List<StoryResponseDTO>>> getStoriesByUser(@PathVariable("userId") Long userId) {
        logger.info("Received request to fetch stories for user: {}", userId);
        return ResponseEntity.ok(ApiResponse.success("User stories fetched successfully", storyService.getStoriesByUser(userId)));
    }

    @RequestMapping(value = "/{id}/view", method = {RequestMethod.PUT, RequestMethod.POST})
    public ResponseEntity<ApiResponse<Void>> viewStory(@PathVariable("id") Long id) {
        Long viewerId = getAuthenticatedUserId();
        logger.info("Received request to view story: {}", id);
        storyService.viewStory(id, viewerId);
        return ResponseEntity.ok(ApiResponse.success("Story viewed successfully", null));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteStory(@PathVariable("id") Long id) {
        Long authorId = getAuthenticatedUserId();
        if (authorId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error(HttpStatus.UNAUTHORIZED.value(), "User not authenticated"));
        }
        logger.info("Received request to delete story {} by user: {}", id, authorId);
        storyService.deleteStory(id, authorId);
        return ResponseEntity.ok(ApiResponse.success("Story deleted successfully", null));
    }
}
