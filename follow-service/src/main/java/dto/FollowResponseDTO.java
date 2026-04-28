package com.connectsphere.follow.dto;

import com.connectsphere.follow.entity.FollowStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class FollowResponseDTO {

    private Long followId;
    private Long followerId;
    private Long followeeId;
    private FollowStatus status;
    private LocalDateTime createdAt;
}
