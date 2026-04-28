package com.connectsphere.like.dto;

import com.connectsphere.like.entity.ReactionType;
import com.connectsphere.like.entity.TargetType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class LikeResponseDTO {

    private Long likeId;
    private Long userId;
    private Long targetId;
    private TargetType targetType;
    private ReactionType reactionType;
    private LocalDateTime createdAt;
}
