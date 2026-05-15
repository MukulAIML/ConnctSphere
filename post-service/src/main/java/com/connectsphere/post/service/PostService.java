package com.connectsphere.post.service;

import com.connectsphere.post.dto.PostRequestDTO;
import com.connectsphere.post.dto.PostResponseDTO;
import com.connectsphere.post.entity.Visibility;

import java.util.List;

public interface PostService {
    PostResponseDTO createPost(PostRequestDTO postRequestDTO, Long authorId);
    PostResponseDTO getPostById(Long postId);
    List<PostResponseDTO> getPostsByUser(Long userId);
    PostResponseDTO updatePost(Long postId, PostRequestDTO postRequestDTO, Long authorId);
    void deletePost(Long postId, Long authorId);
    List<PostResponseDTO> searchPosts(String keyword);
    PostResponseDTO changeVisibility(Long postId, Visibility visibility, Long authorId);
    PostResponseDTO incrementLike(Long postId);
    PostResponseDTO decrementLike(Long postId);
    PostResponseDTO incrementComment(Long postId);
    PostResponseDTO decrementComment(Long postId);
    List<PostResponseDTO> generateFeed(List<Long> followeeIds);
    List<PostResponseDTO> getFeedByUserId(Long userId);
    List<PostResponseDTO> getAllPosts();
    PostResponseDTO updateMediaUrls(Long postId, List<String> newMediaUrls);

    // ── Admin Moderation ──────────────────────────────────────────────────────
    /** Forcefully override a post's content and record an admin note. */
    PostResponseDTO adminForceEditPost(Long postId, String newContent, String adminNote);

    /** Forcefully soft-delete a post and record an admin note. */
    void adminForceDeletePost(Long postId, String adminNote);

    /** Mark a flagged post as reviewed (no further action needed). */
    PostResponseDTO adminMarkReviewed(Long postId, String adminNote);

    /** List all posts that are currently flagged and awaiting review. */
    List<PostResponseDTO> getFlaggedPosts();
}

