package com.connectsphere.comment.controller;

import com.connectsphere.comment.dto.ApiResponse;
import com.connectsphere.comment.dto.CommentRequestDTO;
import com.connectsphere.comment.dto.CommentResponseDTO;
import com.connectsphere.comment.service.CommentService;
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
@RequestMapping("/comments")
public class CommentController {

    private static final Logger logger = LoggerFactory.getLogger(CommentController.class);

    private final CommentService commentService;

    public CommentController(CommentService commentService) {
        this.commentService = commentService;
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
    public ResponseEntity<ApiResponse<CommentResponseDTO>> createComment(@Valid @RequestBody CommentRequestDTO commentRequestDTO) {
        Long authorId = getAuthenticatedUserId();
        logger.info("Received request to create comment by user: {}", authorId);
        CommentResponseDTO createdComment = commentService.createComment(commentRequestDTO, authorId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created("Comment created successfully", createdComment));
    }

    @GetMapping("/post/{postId}")
    public ResponseEntity<ApiResponse<List<CommentResponseDTO>>> getCommentsByPostId(@PathVariable Long postId) {
        logger.info("Received request to get comments for post: {}", postId);
        return ResponseEntity.ok(ApiResponse.success("Comments fetched successfully", commentService.getCommentsByPostId(postId)));
    }

    // Explicitly added to match frontend requirement
    @GetMapping
    public ResponseEntity<ApiResponse<List<CommentResponseDTO>>> getCommentsByPostIdQuery(@RequestParam Long postId) {
        return getCommentsByPostId(postId);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<CommentResponseDTO>> getCommentById(@PathVariable Long id) {
        logger.info("Received request to get comment: {}", id);
        return ResponseEntity.ok(ApiResponse.success("Comment fetched successfully", commentService.getCommentById(id)));
    }

    @GetMapping("/replies/{parentId}")
    public ResponseEntity<ApiResponse<List<CommentResponseDTO>>> getRepliesByParentId(@PathVariable Long parentId) {
        logger.info("Received request to get replies for comment: {}", parentId);
        return ResponseEntity.ok(ApiResponse.success("Replies fetched successfully", commentService.getRepliesByParentId(parentId)));
    }

    @GetMapping("/{id}/replies")
    public ResponseEntity<ApiResponse<List<CommentResponseDTO>>> getRepliesByCommentId(@PathVariable("id") Long id) {
        return getRepliesByParentId(id);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<CommentResponseDTO>> updateComment(@PathVariable Long id, @Valid @RequestBody CommentRequestDTO commentRequestDTO) {
        Long authorId = getAuthenticatedUserId();
        logger.info("Received request to update comment {} by user: {}", id, authorId);
        return ResponseEntity.ok(ApiResponse.success("Comment updated successfully", commentService.updateComment(id, commentRequestDTO, authorId)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteComment(@PathVariable Long id) {
        Long authorId = getAuthenticatedUserId();
        logger.info("Received request to delete comment {} by user: {}", id, authorId);
        commentService.deleteComment(id, authorId);
        return ResponseEntity.ok(ApiResponse.success("Comment deleted successfully", null));
    }

    @PostMapping("/{id}/like")
    public ResponseEntity<ApiResponse<CommentResponseDTO>> likeComment(@PathVariable Long id) {
        logger.info("Received request to like comment: {}", id);
        return ResponseEntity.ok(ApiResponse.success("Comment liked successfully", commentService.likeComment(id)));
    }

    @DeleteMapping("/{id}/like")
    public ResponseEntity<ApiResponse<CommentResponseDTO>> unlikeComment(@PathVariable Long id) {
        logger.info("Received request to unlike comment: {}", id);
        return ResponseEntity.ok(ApiResponse.success("Comment unliked successfully", commentService.unlikeComment(id)));
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<ApiResponse<List<CommentResponseDTO>>> getCommentsByUser(@PathVariable Long userId) {
        logger.info("Received request to get comments for user: {}", userId);
        return ResponseEntity.ok(ApiResponse.success("User comments fetched successfully", commentService.getCommentsByUser(userId)));
    }

    @GetMapping("/count/{postId}")
    public ResponseEntity<ApiResponse<Long>> getCommentCountForPost(@PathVariable Long postId) {
        logger.info("Received request to get comment count for post: {}", postId);
        return ResponseEntity.ok(ApiResponse.success("Comment count fetched successfully", commentService.getCommentCountForPost(postId)));
    }

    @GetMapping("/post/{postId}/count")
    public ResponseEntity<ApiResponse<Long>> getCommentCountForPostAlias(@PathVariable Long postId) {
        return getCommentCountForPost(postId);
    }
}
