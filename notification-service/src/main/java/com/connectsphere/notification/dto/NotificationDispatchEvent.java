package com.connectsphere.notification.dto;

import com.connectsphere.notification.entity.NotificationType;
import com.connectsphere.notification.entity.TargetType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * RabbitMQ message payload for asynchronous notification dispatch.
 * Other micro-services publish this to the connectsphere.exchange with
 * routing key "notification.dispatch".
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class NotificationDispatchEvent {

    private Long recipientId;
    private Long actorId;
    private NotificationType type;
    private String message;
    private Long targetId;
    private TargetType targetType;

    /** Optional: pre-resolved recipient email so the service doesn't need to call auth-service. */
    private String recipientEmail;
}
