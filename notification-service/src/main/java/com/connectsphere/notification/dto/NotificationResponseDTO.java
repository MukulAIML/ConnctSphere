package com.connectsphere.notification.dto;

import com.connectsphere.notification.entity.NotificationType;
import com.connectsphere.notification.entity.TargetType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class NotificationResponseDTO {

    private Long notificationId;
    private Long recipientId;
    private Long actorId;
    private NotificationType type;
    private String message;
    private Long targetId;
    private TargetType targetType;
    private Boolean isRead;
    private LocalDateTime createdAt;
}
