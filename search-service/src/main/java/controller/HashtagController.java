package com.connectsphere.search.controller;

import com.connectsphere.search.dto.ApiResponse;
import com.connectsphere.search.dto.HashtagResponseDTO;
import com.connectsphere.search.service.SearchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping({"/search/hashtags", "/hashtags"})
public class HashtagController {

    private static final Logger logger = LoggerFactory.getLogger(HashtagController.class);
    private final SearchService searchService;

    public HashtagController(SearchService searchService) {
        this.searchService = searchService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<HashtagResponseDTO>>> searchHashtagsDefault(@RequestParam(required = false) String keyword) {
        if (keyword != null && !keyword.isEmpty()) {
            logger.info("Searching hashtags with keyword: {}", keyword);
            return ResponseEntity.ok(ApiResponse.success("Hashtags searched successfully", searchService.searchHashtags(keyword)));
        }
        logger.info("Fetching trending hashtags (default)");
        return ResponseEntity.ok(ApiResponse.success("Trending hashtags fetched successfully", searchService.getTrendingHashtags()));
    }

    @GetMapping("/post/{postId}")
    public ResponseEntity<ApiResponse<List<HashtagResponseDTO>>> getHashtagsForPost(@PathVariable("postId") Long postId) {
        logger.info("Fetching hashtags for post: {}", postId);
        return ResponseEntity.ok(ApiResponse.success("Hashtags fetched successfully", searchService.getHashtagsForPost(postId)));
    }

    @GetMapping("/trending")
    public ResponseEntity<ApiResponse<List<HashtagResponseDTO>>> getTrendingHashtags() {
        logger.info("Fetching trending hashtags");
        return ResponseEntity.ok(ApiResponse.success("Trending hashtags fetched successfully", searchService.getTrendingHashtags()));
    }

    @GetMapping("/{tag}")
    public ResponseEntity<ApiResponse<List<Long>>> getPostsByHashtag(@PathVariable("tag") String tag) {
        logger.info("Fetching posts for hashtag: {}", tag);
        return ResponseEntity.ok(ApiResponse.success("Posts fetched successfully", searchService.getPostsByHashtag(tag)));
    }

    @GetMapping("/search")
    public ResponseEntity<ApiResponse<List<HashtagResponseDTO>>> searchHashtags(@RequestParam String keyword) {
        logger.info("Searching hashtags with keyword: {}", keyword);
        return ResponseEntity.ok(ApiResponse.success("Hashtags searched successfully", searchService.searchHashtags(keyword)));
    }

    @GetMapping("/count/{tag}")
    public ResponseEntity<ApiResponse<Integer>> getHashtagCount(@PathVariable("tag") String tag) {
        logger.info("Fetching usage count for hashtag: {}", tag);
        return ResponseEntity.ok(ApiResponse.success("Hashtag count fetched successfully", searchService.getHashtagCount(tag)));
    }
}
