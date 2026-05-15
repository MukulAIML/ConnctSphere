package com.connectsphere.notification.dto;

import com.connectsphere.notification.entity.NotificationType;
import com.connectsphere.notification.entity.TargetType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class BulkNotificationRequestDTO {

    @NotEmpty(message = "Recipient IDs list cannot be empty")
    private List<Long> recipientIds;

    @NotNull(message = "Actor ID is required")
    private Long actorId;

    @NotNull(message = "Notification type is required")
    private NotificationType type;

    @NotBlank(message = "Message is required")
    private String message;

    @NotNull(message = "Target ID is required")
    private Long targetId;

    @NotNull(message = "Target type is required")
    private TargetType targetType;
}
