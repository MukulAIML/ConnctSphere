package com.connectsphere.follow.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class NotificationRequestDTO {
    private Long recipientId;
    private Long actorId;
    private String type;
    private String message;
    private Long targetId;
    private String targetType;
}
