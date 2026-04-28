package com.connectsphere.search.controller;

import com.connectsphere.search.dto.ApiResponse;
import com.connectsphere.search.dto.IndexRequestDTO;
import com.connectsphere.search.service.SearchService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/search")
public class SearchController {

    private static final Logger logger = LoggerFactory.getLogger(SearchController.class);

    private final SearchService searchService;

    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    @PostMapping("/index")
    public ResponseEntity<ApiResponse<Void>> indexPost(@Valid @RequestBody IndexRequestDTO requestDTO) {
        logger.info("Received request to index post: {}", requestDTO.getPostId());
        searchService.indexPost(requestDTO);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created("Post indexed successfully", null));
    }

    @DeleteMapping("/remove/{postId}")
    public ResponseEntity<ApiResponse<Void>> removePostIndex(@PathVariable("postId") Long postId) {
        logger.info("Received request to remove post index: {}", postId);
        searchService.removePostIndex(postId);
        return ResponseEntity.ok(ApiResponse.success("Post index removed successfully", null));
    }

    @GetMapping("/posts")
    public ResponseEntity<ApiResponse<List<Long>>> searchPostsByKeyword(@RequestParam String keyword) {
        logger.info("Received request to search posts by keyword: {}", keyword);
        return ResponseEntity.ok(ApiResponse.success("Posts searched successfully", searchService.searchPostsByKeyword(keyword)));
    }

    @GetMapping("/users")
    public ResponseEntity<ApiResponse<List<Long>>> searchUsersByKeyword(@RequestParam String keyword) {
        logger.info("Received request to search users by keyword: {}", keyword);
        return ResponseEntity.ok(ApiResponse.success("Users searched successfully", searchService.searchUsersByKeyword(keyword)));
    }
}
