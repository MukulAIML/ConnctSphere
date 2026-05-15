package com.connectsphere.search.controller;

import com.connectsphere.search.dto.ApiResponse;
import com.connectsphere.search.dto.IndexRequestDTO;
import com.connectsphere.search.service.SearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/search")
@Tag(name = "Search", description = "Full-text post and user search backed by Elasticsearch")
public class SearchController {

    private static final Logger logger = LoggerFactory.getLogger(SearchController.class);

    private final SearchService searchService;

    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    @Operation(summary = "Index a post", description = "Extracts hashtags from post content and updates mappings/indexes. Empty content removes existing hashtag mappings for that post. Also callable asynchronously via RabbitMQ.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Post indexed successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid request payload")
    })
    @PostMapping("/index")
    public ResponseEntity<ApiResponse<Void>> indexPost(@Valid @RequestBody IndexRequestDTO requestDTO) {
        logger.info("Received request to index post: {}", requestDTO.getPostId());
        searchService.indexPost(requestDTO);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created("Post indexed successfully", null));
    }

    @Operation(summary = "Remove post index", description = "Removes all hashtag mappings for the given post from Elasticsearch.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Post index removed"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Post not found")
    })
    @DeleteMapping("/remove/{postId}")
    public ResponseEntity<ApiResponse<Void>> removePostIndex(
            @Parameter(description = "ID of the post whose index should be removed") @PathVariable("postId") Long postId) {
        logger.info("Received request to remove post index: {}", postId);
        searchService.removePostIndex(postId);
        return ResponseEntity.ok(ApiResponse.success("Post index removed successfully", null));
    }

    @Operation(summary = "Search posts by keyword", description = "Returns post IDs matching the keyword, using post-service with Elasticsearch fallback.")
    @GetMapping("/posts")
    public ResponseEntity<ApiResponse<List<Long>>> searchPostsByKeyword(
            @Parameter(description = "Keyword to search", required = true) @RequestParam String keyword) {
        logger.info("Received request to search posts by keyword: {}", keyword);
        return ResponseEntity.ok(ApiResponse.success("Posts searched successfully", searchService.searchPostsByKeyword(keyword)));
    }

    @Operation(summary = "Search users by keyword", description = "Returns user IDs whose profiles match the keyword via auth-service.")
    @GetMapping("/users")
    public ResponseEntity<ApiResponse<List<Long>>> searchUsersByKeyword(
            @Parameter(description = "Keyword to search", required = true) @RequestParam String keyword) {
        logger.info("Received request to search users by keyword: {}", keyword);
        return ResponseEntity.ok(ApiResponse.success("Users searched successfully", searchService.searchUsersByKeyword(keyword)));
    }
}
