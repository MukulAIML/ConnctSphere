package com.connectsphere.notification.websocket;

import com.connectsphere.notification.dto.NotificationResponseDTO;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

/**
 * STOMP WebSocket controller.
 *
 * Clients connect via:  ws://<host>:8085/ws-notifications  (SockJS)
 *
 * Subscribe destinations:
 *   /user/{userId}/queue/notifications   – personal notification feed
 *   /topic/typing/{conversationId}       – typing indicators
 *
 * Publish destinations (from clients, optional):
 *   /app/typing  – broadcast a typing indicator
 */
@Controller
public class NotificationWebSocketController {

    private final SimpMessagingTemplate messagingTemplate;

    public NotificationWebSocketController(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * Pushes a live notification to a specific user's personal queue.
     * Called internally by NotificationServiceImpl after persisting.
     */
    public void pushNotificationToUser(Long recipientId, NotificationResponseDTO notification) {
        messagingTemplate.convertAndSendToUser(
                recipientId.toString(),
                "/queue/notifications",
                notification
        );
    }

    /**
     * STOMP endpoint: clients send a typing indicator payload here.
     * The service broadcasts it to all subscribers of the conversation topic.
     *
     * Payload example: { "conversationId": "42", "userId": "7", "typing": true }
     */
    @MessageMapping("/typing")
    public void handleTypingIndicator(@Payload TypingIndicatorPayload payload) {
        messagingTemplate.convertAndSend(
                "/topic/typing/" + payload.getConversationId(),
                payload
        );
    }
}
