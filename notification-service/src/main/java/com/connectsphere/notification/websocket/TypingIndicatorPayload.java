package com.connectsphere.notification.websocket;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TypingIndicatorPayload {
    private String conversationId;
    private String userId;
    private boolean typing;
}
