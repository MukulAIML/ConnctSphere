package com.connectsphere.commentservice.entity;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Entity
@Table(name = "comments", indexes = { @Index(name = "idx_comment_post", columnList = "postId"),
		@Index(name = "idx_comment_author", columnList = "authorId"),
		@Index(name = "idx_comment_parent", columnList = "parentCommentId"),
		@Index(name = "idx_comment_deleted", columnList = "isDeleted") })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
@EqualsAndHashCode(of = "commentId")
public class Comment {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private int commentId;

	@Column(nullable = false)
	private int postId;

	@Column(nullable = false)
	private int authorId;

	@Column
	private Integer parentCommentId;

	@NotBlank
	@Size(max = 2000)
	@Column(nullable = false, length = 2000)
	private String content;

	@Column(nullable = false)
	@Builder.Default
	private int likesCount = 0;

	@Column(nullable = false)
	@Builder.Default
	private boolean isDeleted = false;

	@CreationTimestamp
	@Column(nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@UpdateTimestamp
	@Column(nullable = false)
	private LocalDateTime updatedAt;

	@Transient
	@Builder.Default
	private List<Comment> replies = new ArrayList<>();

	public boolean isReply() {
		return parentCommentId != null;
	}
}
