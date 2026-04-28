package com.connectsphere.like.dto;

import com.connectsphere.like.entity.ReactionType;
import com.connectsphere.like.entity.TargetType;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class LikeRequestDTO {

    @NotNull(message = "Target ID is required")
    private Long targetId;

    @NotNull(message = "Target Type is required")
    private TargetType targetType;

    @NotNull(message = "Reaction Type is required")
    private ReactionType reactionType;
}
