package com.connectsphere.search.controller;

import com.connectsphere.search.dto.ApiResponse;
import com.connectsphere.search.dto.HashtagResponseDTO;
import com.connectsphere.search.service.SearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping({"/search/hashtags", "/hashtags"})
@Tag(name = "Hashtags", description = "Hashtag lookup and trending computation powered by Elasticsearch")
public class HashtagController {

    private static final Logger logger = LoggerFactory.getLogger(HashtagController.class);
    private final SearchService searchService;

    public HashtagController(SearchService searchService) {
        this.searchService = searchService;
    }

    @Operation(summary = "Search or list trending hashtags",
            description = "If 'keyword' is provided, searches hashtags by keyword. Otherwise returns the top-10 trending hashtags.")
    @GetMapping
    public ResponseEntity<ApiResponse<List<HashtagResponseDTO>>> searchHashtagsDefault(
            @Parameter(description = "Optional keyword to filter hashtags") @RequestParam(required = false) String keyword) {
        if (keyword != null && !keyword.isEmpty()) {
            logger.info("Searching hashtags with keyword: {}", keyword);
            return ResponseEntity.ok(ApiResponse.success("Hashtags searched successfully", searchService.searchHashtags(keyword)));
        }
        logger.info("Fetching trending hashtags (default)");
        return ResponseEntity.ok(ApiResponse.success("Trending hashtags fetched successfully", searchService.getTrendingHashtags()));
    }

    @Operation(summary = "Get hashtags for a post")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Hashtags fetched"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Post not found")
    })
    @GetMapping("/post/{postId}")
    public ResponseEntity<ApiResponse<List<HashtagResponseDTO>>> getHashtagsForPost(
            @Parameter(description = "ID of the post") @PathVariable("postId") Long postId) {
        logger.info("Fetching hashtags for post: {}", postId);
        return ResponseEntity.ok(ApiResponse.success("Hashtags fetched successfully", searchService.getHashtagsForPost(postId)));
    }

    @Operation(summary = "Get top-10 trending hashtags", description = "Returns the 10 most-used hashtags ordered by post count descending.")
    @GetMapping("/trending")
    public ResponseEntity<ApiResponse<List<HashtagResponseDTO>>> getTrendingHashtags() {
        logger.info("Fetching trending hashtags");
        return ResponseEntity.ok(ApiResponse.success("Trending hashtags fetched successfully", searchService.getTrendingHashtags()));
    }

    @Operation(summary = "Get post IDs by hashtag tag value")
    @GetMapping("/{tag}")
    public ResponseEntity<ApiResponse<List<Long>>> getPostsByHashtag(
            @Parameter(description = "Hashtag text (without #)") @PathVariable("tag") String tag) {
        logger.info("Fetching posts for hashtag: {}", tag);
        return ResponseEntity.ok(ApiResponse.success("Posts fetched successfully", searchService.getPostsByHashtag(tag)));
    }

    @Operation(summary = "Search hashtags by keyword")
    @GetMapping("/search")
    public ResponseEntity<ApiResponse<List<HashtagResponseDTO>>> searchHashtags(
            @Parameter(description = "Keyword to search", required = true) @RequestParam String keyword) {
        logger.info("Searching hashtags with keyword: {}", keyword);
        return ResponseEntity.ok(ApiResponse.success("Hashtags searched successfully", searchService.searchHashtags(keyword)));
    }

    @Operation(summary = "Get usage count for a specific hashtag")
    @GetMapping("/count/{tag}")
    public ResponseEntity<ApiResponse<Integer>> getHashtagCount(
            @Parameter(description = "Hashtag text (without #)") @PathVariable("tag") String tag) {
        logger.info("Fetching usage count for hashtag: {}", tag);
        return ResponseEntity.ok(ApiResponse.success("Hashtag count fetched successfully", searchService.getHashtagCount(tag)));
    }
}
