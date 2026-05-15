package com.connectsphere.commentservice.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.connectsphere.commentservice.entity.CommentLike;

@Repository
public interface CommentLikeRepository extends JpaRepository<CommentLike, Integer> {

	Optional<CommentLike> findByCommentIdAndUserId(int commentId, int userId);

	void deleteByCommentId(int commentId);
}
