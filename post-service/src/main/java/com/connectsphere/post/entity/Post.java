package com.connectsphere.post.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "posts")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Post {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long postId;

    @Column(nullable = false)
    private Long authorId;

    @Column(columnDefinition = "TEXT", nullable = true)
    private String content;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "post_media_urls", joinColumns = @JoinColumn(name = "post_id"))
    @Column(name = "url")
    private List<String> mediaUrls;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PostType postType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Visibility visibility;

    @Column(nullable = false)
    @Builder.Default
    private Integer likesCount = 0;

    @Column(nullable = false)
    @Builder.Default
    private Integer commentsCount = 0;

    @Column(nullable = false)
    @Builder.Default
    private Integer sharesCount = 0;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    @Column(nullable = false)
    @Builder.Default
    private Boolean isDeleted = false;

    // ── Content Moderation Fields ────────────────────────────────────────────
    /** Whether the post has been flagged by the AI moderation pipeline. */
    @Column(nullable = false)
    @Builder.Default
    private Boolean isFlagged = false;

    /** Free-text label from Rekognition (e.g. "Explicit Nudity", "Violence"). */
    @Column
    private String moderationLabel;

    /** Rekognition confidence score for the top moderation label (0-100). */
    @Column
    private Float moderationScore;

    /** True once a human admin has reviewed and taken action on this post. */
    @Column(nullable = false)
    @Builder.Default
    private Boolean moderationReviewed = false;

    /** Admin-supplied note when the post is force-edited or force-removed. */
    @Column(columnDefinition = "TEXT")
    private String adminNote;
}
