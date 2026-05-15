package com.connectsphere.post.repository;

import com.connectsphere.post.entity.Post;
import com.connectsphere.post.entity.Visibility;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PostRepository extends JpaRepository<Post, Long> {

    List<Post> findByAuthorIdAndIsDeletedFalseOrderByCreatedAtDesc(Long authorId);

    List<Post> findByAuthorIdAndIsDeletedFalseAndVisibilityOrderByCreatedAtDesc(Long authorId, Visibility visibility);

    List<Post> findByAuthorIdAndIsDeletedFalseAndVisibilityInOrderByCreatedAtDesc(Long authorId, List<Visibility> visibilities);

    List<Post> findByIsDeletedFalseAndVisibilityOrderByCreatedAtDesc(Visibility visibility);

    List<Post> findByIsDeletedFalseOrderByCreatedAtDesc();

    @Query("SELECT p FROM Post p WHERE p.isDeleted = false AND LOWER(p.content) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
            "AND p.visibility = com.connectsphere.post.entity.Visibility.PUBLIC ORDER BY p.createdAt DESC")
    List<Post> searchPublicPostsByKeyword(@Param("keyword") String keyword);

    @Query("""
            SELECT p FROM Post p
            WHERE p.isDeleted = false
              AND LOWER(p.content) LIKE LOWER(CONCAT('%', :keyword, '%'))
              AND (
                    p.visibility = com.connectsphere.post.entity.Visibility.PUBLIC
                 OR p.authorId = :viewerId
                 OR (p.visibility = com.connectsphere.post.entity.Visibility.FOLLOWERS_ONLY AND p.authorId IN :followeeIds)
              )
            ORDER BY p.createdAt DESC
            """)
    List<Post> searchVisiblePostsByKeyword(
            @Param("keyword") String keyword,
            @Param("viewerId") Long viewerId,
            @Param("followeeIds") List<Long> followeeIds);

    @Query("""
            SELECT p FROM Post p
            WHERE p.isDeleted = false
              AND (
                    p.visibility = com.connectsphere.post.entity.Visibility.PUBLIC
                 OR p.authorId = :viewerId
                 OR (p.visibility = com.connectsphere.post.entity.Visibility.FOLLOWERS_ONLY AND p.authorId IN :followeeIds)
              )
            ORDER BY p.createdAt DESC
            """)
    List<Post> findVisiblePostsForViewerWithFollowees(
            @Param("viewerId") Long viewerId,
            @Param("followeeIds") List<Long> followeeIds);

    @Query("""
            SELECT p FROM Post p
            WHERE p.isDeleted = false
              AND (p.visibility = com.connectsphere.post.entity.Visibility.PUBLIC OR p.authorId = :viewerId)
            ORDER BY p.createdAt DESC
            """)
    List<Post> findVisiblePostsForViewerWithoutFollowees(@Param("viewerId") Long viewerId);

    @Query("""
            SELECT p FROM Post p
            WHERE p.isDeleted = false
              AND (p.authorId = :viewerId OR (
                    p.authorId IN :followeeIds
                AND p.visibility IN (
                    com.connectsphere.post.entity.Visibility.PUBLIC,
                    com.connectsphere.post.entity.Visibility.FOLLOWERS_ONLY
                )
              ))
            ORDER BY p.createdAt DESC
            """)
    List<Post> findPersonalizedFeedForUserWithFollowees(
            @Param("viewerId") Long viewerId,
            @Param("followeeIds") List<Long> followeeIds);

    @Query("""
            SELECT p FROM Post p
            WHERE p.isDeleted = false
              AND p.authorId = :viewerId
            ORDER BY p.createdAt DESC
            """)
    List<Post> findPersonalizedFeedForUserWithoutFollowees(@Param("viewerId") Long viewerId);

    @Query("""
            SELECT p FROM Post p
            WHERE p.isDeleted = false
              AND p.authorId IN :followeeIds
              AND p.visibility IN (
                    com.connectsphere.post.entity.Visibility.PUBLIC,
                    com.connectsphere.post.entity.Visibility.FOLLOWERS_ONLY
              )
            ORDER BY p.createdAt DESC
            """)
    List<Post> findVisibleFeedByFolloweeIds(@Param("followeeIds") List<Long> followeeIds);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE Post p
               SET p.likesCount = p.likesCount + 1
             WHERE p.postId = :postId
               AND p.isDeleted = false
            """)
    int incrementLikesCount(@Param("postId") Long postId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE Post p
               SET p.likesCount = CASE WHEN p.likesCount > 0 THEN p.likesCount - 1 ELSE 0 END
             WHERE p.postId = :postId
               AND p.isDeleted = false
            """)
    int decrementLikesCount(@Param("postId") Long postId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE Post p
               SET p.commentsCount = p.commentsCount + 1
             WHERE p.postId = :postId
               AND p.isDeleted = false
            """)
    int incrementCommentsCount(@Param("postId") Long postId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE Post p
               SET p.commentsCount = CASE WHEN p.commentsCount > 0 THEN p.commentsCount - 1 ELSE 0 END
             WHERE p.postId = :postId
               AND p.isDeleted = false
            """)
    int decrementCommentsCount(@Param("postId") Long postId);

    /** Returns all non-deleted posts flagged by the AI moderation pipeline. */
    List<Post> findByIsFlaggedTrueAndIsDeletedFalseOrderByCreatedAtDesc();

    long countByIsDeletedFalse();
}
