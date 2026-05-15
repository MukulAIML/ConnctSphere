package com.connectsphere.commentservice.service;

import com.connectsphere.commentservice.client.PostServiceClient;
import com.connectsphere.commentservice.dto.AdminDeleteCommentResponse;
import com.connectsphere.commentservice.entity.Comment;
import com.connectsphere.commentservice.exception.ResourceNotFoundException;
import com.connectsphere.commentservice.repository.CommentLikeRepository;
import com.connectsphere.commentservice.repository.CommentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class AdminModerationServiceImpl implements AdminModerationService {

    private final CommentRepository commentRepository;
    private final CommentLikeRepository commentLikeRepository;
    private final PostServiceClient postServiceClient;

    // -----------------------------------------------------------------------
    // Single comment
    // -----------------------------------------------------------------------

    @Override
    public AdminDeleteCommentResponse forceDeleteComment(int commentId, int adminId, String reason) {
        Comment comment = requireComment(commentId);

        // Cascade to replies when removing a top-level comment
        int repliesDeleted = 0;
        if (!comment.isReply()) {
            List<Comment> replies =
                    commentRepository.findByParentCommentIdAndIsDeletedFalseOrderByCreatedAtAsc(commentId);
            repliesDeleted = replies.size();
            if (repliesDeleted > 0) {
                commentRepository.softDeleteRepliesByParentCommentId(commentId);
                for (Comment reply : replies) {
                    commentLikeRepository.deleteByCommentId(reply.getCommentId());
                    safeDecrementPost(comment.getPostId());
                }
                log.info("[ADMIN-MOD] adminId={} cascade-deleted {} replies of commentId={}", adminId, repliesDeleted, commentId);
            }
        }

        commentRepository.softDeleteByCommentId(commentId);
        commentLikeRepository.deleteByCommentId(commentId);
        safeDecrementPost(comment.getPostId());

        log.info("[ADMIN-MOD] adminId={} force-deleted commentId={} (postId={}, authorId={}) reason='{}'",
                adminId, commentId, comment.getPostId(), comment.getAuthorId(), reason);

        return AdminDeleteCommentResponse.builder()
                .commentId(commentId)
                .totalDeleted(1 + repliesDeleted)
                .repliesDeleted(repliesDeleted)
                .postId(comment.getPostId())
                .originalAuthorId(comment.getAuthorId())
                .deletedByAdminId(adminId)
                .reason(reason)
                .deletedAt(LocalDateTime.now())
                .build();
    }

    // -----------------------------------------------------------------------
    // All comments by a user
    // -----------------------------------------------------------------------

    @Override
    public List<AdminDeleteCommentResponse> forceDeleteAllCommentsByUser(int targetUserId, int adminId, String reason) {
        // Only operate on top-level comments; replies are handled via cascade inside forceDeleteComment
        List<Comment> topLevel = commentRepository
                .findByAuthorIdAndIsDeletedFalseOrderByCreatedAtDesc(targetUserId)
                .stream()
                .filter(c -> !c.isReply())
                .toList();

        List<AdminDeleteCommentResponse> results = new ArrayList<>();
        for (Comment c : topLevel) {
            results.add(forceDeleteComment(c.getCommentId(), adminId, reason));
        }

        // Now handle orphan replies that weren't cascaded (replies whose parent was already deleted before)
        List<Comment> orphanReplies = commentRepository
                .findByAuthorIdAndIsDeletedFalseOrderByCreatedAtDesc(targetUserId);
        for (Comment reply : orphanReplies) {
            if (reply.isReply()) {
                commentRepository.softDeleteByCommentId(reply.getCommentId());
                commentLikeRepository.deleteByCommentId(reply.getCommentId());
                safeDecrementPost(reply.getPostId());
                log.info("[ADMIN-MOD] adminId={} force-deleted orphan replyId={} for userId={}", adminId, reply.getCommentId(), targetUserId);
                results.add(AdminDeleteCommentResponse.builder()
                        .commentId(reply.getCommentId())
                        .totalDeleted(1)
                        .repliesDeleted(0)
                        .postId(reply.getPostId())
                        .originalAuthorId(reply.getAuthorId())
                        .deletedByAdminId(adminId)
                        .reason(reason)
                        .deletedAt(LocalDateTime.now())
                        .build());
            }
        }

        log.info("[ADMIN-MOD] adminId={} purged {} comment records for userId={}", adminId, results.size(), targetUserId);
        return results;
    }

    // -----------------------------------------------------------------------
    // All comments on a post
    // -----------------------------------------------------------------------

    @Override
    public List<AdminDeleteCommentResponse> forceDeleteAllCommentsByPost(int postId, int adminId, String reason) {
        List<Comment> topLevel = commentRepository.findTopLevelByPostId(postId);

        List<AdminDeleteCommentResponse> results = new ArrayList<>();
        for (Comment c : topLevel) {
            results.add(forceDeleteComment(c.getCommentId(), adminId, reason));
        }

        log.info("[ADMIN-MOD] adminId={} purged all comments for postId={} ({} top-level records)", adminId, postId, results.size());
        return results;
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private Comment requireComment(int commentId) {
        return commentRepository.findByCommentIdAndIsDeletedFalse(commentId).orElseThrow(
                () -> new ResourceNotFoundException("Comment not found or already deleted: commentId=" + commentId));
    }

    private void safeDecrementPost(int postId) {
        try {
            postServiceClient.decrementCommentCount(postId);
        } catch (Exception ex) {
            log.error("[ADMIN-MOD] Failed to decrement commentsCount for postId={}: {}", postId, ex.getMessage());
        }
    }
}
