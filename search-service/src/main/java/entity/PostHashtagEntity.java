package com.connectsphere.search.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "post_hashtags")
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
