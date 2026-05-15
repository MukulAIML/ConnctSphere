package com.connectsphere.likeservice.entity;

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
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Entity
@Table(
    name = "likes",
    uniqueConstraints = {
        // One reaction per user per target — uses actual DB column names (snake_case)
        @UniqueConstraint(name = "like_user_target", columnNames = { "user_id", "target_id", "target_type" })
    },
    indexes = {
        @Index(name = "idx_like_target",   columnList = "target_id, target_type"),
        @Index(name = "idx_like_user",     columnList = "user_id"),
        @Index(name = "idx_like_reaction", columnList = "target_id, reaction_type")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
@EqualsAndHashCode(of = "likeId")
public class Like {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long likeId;

    @Column(name = "user_id", nullable = false)
    private int userId;

    @Column(name = "target_id", nullable = false)
    private int targetId;

    @Column(name = "target_type", nullable = false, length = 19)
    private String targetType;

    @Column(name = "reaction_type", nullable = false)
    @Builder.Default
    private String reactionType = "LIKE";

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public static final String TARGET_POST    = "POST";
    public static final String TARGET_COMMENT = "COMMENT";

    public static final String REACTION_LIKE  = "LIKE";
    public static final String REACTION_LOVE  = "LOVE";
    public static final String REACTION_HAHA  = "HAHA";
    public static final String REACTION_WOW   = "WOW";
    public static final String REACTION_SAD   = "SAD";
    public static final String REACTION_ANGRY = "ANGRY";

    public boolean targetsPost() {
        return TARGET_POST.equals(targetType);
    }

    public boolean targetsComment() {
        return TARGET_COMMENT.equals(targetType);
    }
}
