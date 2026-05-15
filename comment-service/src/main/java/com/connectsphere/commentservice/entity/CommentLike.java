package com.connectsphere.commentservice.entity;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "comment_likes", uniqueConstraints = {
		@UniqueConstraint(name = "uk_comment_user_like", columnNames = { "comment_id", "user_id" }) }, indexes = {
				@Index(name = "idx_comment_like_comment", columnList = "comment_id"),
				@Index(name = "idx_comment_like_user", columnList = "user_id") })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CommentLike {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Integer commentLikeId;

	@Column(name = "comment_id", nullable = false)
	private Integer commentId;

	@Column(name = "user_id", nullable = false)
	private Integer userId;

	@CreationTimestamp
	@Column(nullable = false, updatable = false)
	private LocalDateTime createdAt;
}
