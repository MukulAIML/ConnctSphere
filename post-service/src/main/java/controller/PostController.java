package com.connectsphere.post.controller;

import com.connectsphere.post.dto.PostResponseDTO;
import com.connectsphere.post.entity.Visibility;
import com.connectsphere.post.service.PostService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/posts")
public class PostController {

    private final PostService postService;

    public PostController(PostService postService) {
        this.postService = postService;
    }

    private Long getAuthenticatedUserId() {
        org.springframework.security.core.Authentication authentication = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() != null) {
            try {
                return Long.valueOf(authentication.getPrincipal().toString());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    // ✅ CREATE API
    @PostMapping
    public PostResponseDTO createPost(@jakarta.validation.Valid @RequestBody com.connectsphere.post.dto.PostRequestDTO requestDTO) {
        Long authorId = getAuthenticatedUserId();
        if (authorId == null) {
            throw new com.connectsphere.post.exception.UnauthorizedAccessException("User not authenticated");
        }
        return postService.createPost(requestDTO, authorId);
    }

    // ✅ FEED API (MOST IMPORTANT)
    @GetMapping("/feed/{userId}")
    public List<PostResponseDTO> getFeed(@PathVariable Long userId) {
        return postService.getFeedByUserId(userId);
    }

    @PostMapping("/feed")
    public List<PostResponseDTO> generateFeed(@RequestBody List<Long> followeeIds) {
        return postService.generateFeed(followeeIds);
    }

    // ✅ GET POSTS BY USER (For Profile)
    @GetMapping("/user/{userId}")
    public List<PostResponseDTO> getPostsByUser(@PathVariable Long userId) {
        return postService.getPostsByUser(userId);
    }

    // ✅ GET SINGLE POST
    @GetMapping("/{postId}")
    public PostResponseDTO getPostById(@PathVariable Long postId) {
        return postService.getPostById(postId);
    }

    @PutMapping("/{postId}")
    public PostResponseDTO updatePost(
            @PathVariable Long postId,
            @jakarta.validation.Valid @RequestBody com.connectsphere.post.dto.PostRequestDTO requestDTO
    ) {
        Long authorId = getAuthenticatedUserId();
        if (authorId == null) {
            throw new com.connectsphere.post.exception.UnauthorizedAccessException("User not authenticated");
        }
        return postService.updatePost(postId, requestDTO, authorId);
    }

    @DeleteMapping("/{postId}")
    public void deletePost(@PathVariable Long postId) {
        Long authorId = getAuthenticatedUserId();
        if (authorId == null) {
            throw new com.connectsphere.post.exception.UnauthorizedAccessException("User not authenticated");
        }
        postService.deletePost(postId, authorId);
    }

    // ✅ GET ALL POSTS
    @GetMapping
    public List<PostResponseDTO> getAllPosts() {
        return postService.getAllPosts();
    }

    @GetMapping("/search")
    public List<PostResponseDTO> searchPosts(@RequestParam String keyword) {
        return postService.searchPosts(keyword);
    }

    @PutMapping("/{postId}/visibility")
    public PostResponseDTO changeVisibility(@PathVariable Long postId, @RequestParam Visibility visibility) {
        Long authorId = getAuthenticatedUserId();
        if (authorId == null) {
            throw new com.connectsphere.post.exception.UnauthorizedAccessException("User not authenticated");
        }
        return postService.changeVisibility(postId, visibility, authorId);
    }

    // ✅ MEDIA SYNC API
    @PutMapping("/{postId}/mediaUrls")
    public PostResponseDTO updateMediaUrls(@PathVariable Long postId, @RequestBody com.connectsphere.post.dto.MediaUrlsUpdateDTO dto) {
        return postService.updateMediaUrls(postId, dto.getMediaUrls());
    }

    @PutMapping("/{postId}/like/increment")
    public PostResponseDTO incrementLike(@PathVariable Long postId) {
        return postService.incrementLike(postId);
    }

    @PutMapping("/{postId}/like/decrement")
    public PostResponseDTO decrementLike(@PathVariable Long postId) {
        return postService.decrementLike(postId);
    }

    @PutMapping("/{postId}/comment/increment")
    public PostResponseDTO incrementComment(@PathVariable Long postId) {
        return postService.incrementComment(postId);
    }

    @PutMapping("/{postId}/comment/decrement")
    public PostResponseDTO decrementComment(@PathVariable Long postId) {
        return postService.decrementComment(postId);
    }

    // ✅ TEST API (to verify controller working)
    @GetMapping("/test")
    public String test() {
        return "POST SERVICE WORKING";
    }
}
