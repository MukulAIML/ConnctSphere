package com.connectsphere.search.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "post_hashtags",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_post_hashtag", columnNames = {"post_id", "hashtag_id"})
        },
        indexes = {
                @Index(name = "idx_post_hashtags_post_id", columnList = "post_id"),
                @Index(name = "idx_post_hashtags_hashtag_id", columnList = "hashtag_id")
        }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PostHashtagEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long postId;

    @Column(nullable = false)
    private Long hashtagId;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;
}
