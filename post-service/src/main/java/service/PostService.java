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
}
