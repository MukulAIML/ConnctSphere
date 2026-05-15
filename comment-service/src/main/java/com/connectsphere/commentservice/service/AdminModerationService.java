package com.connectsphere.commentservice.service;

import com.connectsphere.commentservice.dto.AdminDeleteCommentResponse;

import java.util.List;

/**
 * Administrative moderation operations on comments.
 * All methods bypass owner-checks and may be invoked only by ROLE_ADMIN principals.
 */
public interface AdminModerationService {

    /**
     * Forcefully remove a single comment (and its replies) regardless of authorship.
     *
     * @param commentId the comment to remove
     * @param adminId   the admin user ID performing the action (for audit logging)
     * @param reason    optional human-readable reason for removal
     * @return summary of what was deleted
     */
    AdminDeleteCommentResponse forceDeleteComment(int commentId, int adminId, String reason);

    /**
     * Bulk-remove all comments belonging to a specific user (e.g. after a ban).
     *
     * @param targetUserId the user whose comments should be purged
     * @param adminId      the admin user ID performing the action
     * @param reason       optional reason
     * @return list of per-comment deletion summaries
     */
    List<AdminDeleteCommentResponse> forceDeleteAllCommentsByUser(int targetUserId, int adminId, String reason);

    /**
     * Bulk-remove all comments on a specific post (e.g. post is being removed).
     *
     * @param postId  the post whose comments should be purged
     * @param adminId the admin user ID performing the action
     * @param reason  optional reason
     * @return list of per-comment deletion summaries
     */
    List<AdminDeleteCommentResponse> forceDeleteAllCommentsByPost(int postId, int adminId, String reason);
}
