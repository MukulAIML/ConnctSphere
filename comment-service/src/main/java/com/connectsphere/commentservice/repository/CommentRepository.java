package com.connectsphere.commentservice.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.connectsphere.commentservice.entity.Comment;

@Repository
public interface CommentRepository extends JpaRepository<Comment, Integer> {

	List<Comment> findByPostIdAndIsDeletedFalseOrderByCreatedAtAsc(int postId);

	@Query("SELECT c FROM Comment c WHERE c.postId = :postId "
			+ "AND c.parentCommentId IS NULL AND c.isDeleted = false " + "ORDER BY c.createdAt ASC")
	List<Comment> findTopLevelByPostId(@Param("postId") int postId);

	List<Comment> findByPostIdAndParentCommentIdIsNotNullAndIsDeletedFalseOrderByCreatedAtAsc(int postId);

	List<Comment> findByParentCommentIdAndIsDeletedFalseOrderByCreatedAtAsc(int parentCommentId);

	int countByPostIdAndIsDeletedFalse(int postId);

	Optional<Comment> findByCommentIdAndIsDeletedFalse(int commentId);

	List<Comment> findByAuthorIdAndIsDeletedFalseOrderByCreatedAtDesc(int authorId);

	@Modifying
	@Query("UPDATE Comment c SET c.isDeleted = true WHERE c.commentId = :commentId")
	void softDeleteByCommentId(@Param("commentId") int commentId);

	@Modifying
	@Query("UPDATE Comment c SET c.isDeleted = true WHERE c.parentCommentId = :parentCommentId")
	void softDeleteRepliesByParentCommentId(@Param("parentCommentId") int parentCommentId);

	@Modifying
	@Query("UPDATE Comment c SET c.likesCount = c.likesCount + 1 WHERE c.commentId = :commentId AND c.isDeleted = false")
	int incrementLikesCount(@Param("commentId") int commentId);

	@Modifying
	@Query("UPDATE Comment c SET c.likesCount = GREATEST(c.likesCount - 1, 0) WHERE c.commentId = :commentId AND c.isDeleted = false")
	int decrementLikesCount(@Param("commentId") int commentId);

	@Query("SELECT CASE WHEN c.parentCommentId IS NULL THEN true ELSE false END "
			+ "FROM Comment c WHERE c.commentId = :commentId AND c.isDeleted = false")
	Boolean isTopLevel(@Param("commentId") int commentId);

	int countByParentCommentIdAndIsDeletedFalse(int parentCommentId);
}
