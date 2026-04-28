package com.connectsphere.comment.service;

import com.connectsphere.comment.dto.CommentRequestDTO;
import com.connectsphere.comment.dto.CommentResponseDTO;

import java.util.List;

public interface CommentService {
    CommentResponseDTO createComment(CommentRequestDTO commentRequestDTO, Long authorId);
    CommentResponseDTO getCommentById(Long commentId);
    List<CommentResponseDTO> getCommentsByPostId(Long postId);
    List<CommentResponseDTO> getRepliesByParentId(Long parentId);
    List<CommentResponseDTO> getCommentsByUser(Long authorId);
    CommentResponseDTO updateComment(Long commentId, CommentRequestDTO commentRequestDTO, Long authorId);
    void deleteComment(Long commentId, Long authorId);
    CommentResponseDTO likeComment(Long commentId);
    CommentResponseDTO unlikeComment(Long commentId);
    long getCommentCountForPost(Long postId);
}
