package com.connectsphere.post.repository;

import com.connectsphere.post.entity.Post;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PostRepository extends JpaRepository<Post, Long> {

    List<Post> findByAuthorIdAndIsDeletedFalseOrderByCreatedAtDesc(Long authorId);

    @Query("SELECT p FROM Post p WHERE p.isDeleted = false AND " +
            "(LOWER(p.content) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    List<Post> searchPostsByKeyword(@Param("keyword") String keyword);

    @Query("SELECT p FROM Post p WHERE p.isDeleted = false AND (p.authorId IN :followeeIds OR p.visibility = com.connectsphere.post.entity.Visibility.PUBLIC) ORDER BY p.createdAt DESC")
    List<Post> findFeedByFolloweeIds(@Param("followeeIds") List<Long> followeeIds);
}
