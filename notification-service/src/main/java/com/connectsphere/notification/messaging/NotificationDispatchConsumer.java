package com.connectsphere.notification.messaging;

import com.connectsphere.notification.dto.NotificationDispatchEvent;
import com.connectsphere.notification.dto.NotificationRequestDTO;
import com.connectsphere.notification.entity.NotificationType;
import com.connectsphere.notification.entity.TargetType;
import com.connectsphere.notification.service.NotificationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;

@Component
public class NotificationDispatchConsumer {

    private static final Logger logger = LoggerFactory.getLogger(NotificationDispatchConsumer.class);

    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;

    public NotificationDispatchConsumer(NotificationService notificationService, ObjectMapper objectMapper) {
        this.notificationService = notificationService;
        this.objectMapper = objectMapper;
    }

    @RabbitListener(queues = "${app.rabbitmq.queue.notification-dispatch}")
    public void handleNotificationDispatch(Object payload) {
        try {
            NotificationDispatchEvent event = normalizePayload(payload);
            validateEvent(event);
            logger.info("[RabbitMQ] Received notification dispatch event: type={} recipientId={}",
                    event.getType(), event.getRecipientId());

            NotificationRequestDTO dto = NotificationRequestDTO.builder()
                    .recipientId(event.getRecipientId())
                    .actorId(event.getActorId())
                    .type(event.getType())
                    .message(event.getMessage())
                    .targetId(event.getTargetId())
                    .targetType(event.getTargetType())
                    .build();

            notificationService.createNotification(dto);
            logger.info("[RabbitMQ] Notification created for recipientId={}", event.getRecipientId());
        } catch (IllegalArgumentException e) {
            // Invalid event payload should not poison the queue with infinite retries.
            logger.warn("[RabbitMQ] Discarding invalid notification event: {}", e.getMessage());
        } catch (Exception e) {
            logger.error("[RabbitMQ] Failed to dispatch notification: {}", e.getMessage(), e);
            throw e; // let RabbitMQ handle retry / DLQ
        }
    }

    private NotificationDispatchEvent normalizePayload(Object payload) {
        if (payload instanceof NotificationDispatchEvent event) {
            return event;
        }

        if (payload instanceof Message amqpMessage) {
            return normalizePayload(readAmqpPayload(amqpMessage));
        }

        if (payload instanceof byte[] bytes) {
            return normalizePayload(readJsonPayload(bytes));
        }

        if (payload instanceof String text) {
            return normalizePayload(text.getBytes(StandardCharsets.UTF_8));
        }

        if (payload instanceof Map<?, ?> map) {
            return NotificationDispatchEvent.builder()
                    .recipientId(asLong(map.get("recipientId")))
                    .actorId(asLong(map.get("actorId")))
                    .type(asNotificationType(map.get("type")))
                    .message(asString(map.get("message")))
                    .targetId(asLong(map.get("targetId")))
                    .targetType(asTargetType(map.get("targetType")))
                    .recipientEmail(asString(map.get("recipientEmail")))
                    .build();
        }

        throw new IllegalArgumentException("Unsupported notification payload type: "
                + (payload == null ? "null" : payload.getClass().getName()));
    }

    private Object readAmqpPayload(Message message) {
        byte[] body = message.getBody();
        if (body == null || body.length == 0) {
            throw new IllegalArgumentException("AMQP message body is empty");
        }
        return readJsonPayload(body);
    }

    private Object readJsonPayload(byte[] bytes) {
        try {
            return objectMapper.readValue(bytes, Map.class);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Unable to deserialize notification payload", ex);
        }
    }

    private Long asLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        String text = value.toString().trim();
        if (text.isEmpty()) {
            return null;
        }
        return Long.valueOf(text);
    }

    private String asString(Object value) {
        if (value == null) {
            return null;
        }
        String text = value.toString();
        return text.isBlank() ? null : text;
    }

    private NotificationType asNotificationType(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof NotificationType type) {
            return type;
        }
        return NotificationType.valueOf(value.toString().trim().toUpperCase(Locale.ROOT));
    }

    private TargetType asTargetType(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof TargetType type) {
            return type;
        }
        return TargetType.valueOf(value.toString().trim().toUpperCase(Locale.ROOT));
    }

    private void validateEvent(NotificationDispatchEvent event) {
        if (event.getRecipientId() == null || event.getRecipientId() <= 0) {
            throw new IllegalArgumentException("recipientId must be greater than 0");
        }
        if (event.getActorId() == null || event.getActorId() <= 0) {
            throw new IllegalArgumentException("actorId must be greater than 0");
        }
        if (event.getTargetId() == null || event.getTargetId() <= 0) {
            throw new IllegalArgumentException("targetId must be greater than 0");
        }
        if (event.getType() == null) {
            throw new IllegalArgumentException("type is required");
        }
        if (event.getTargetType() == null) {
            throw new IllegalArgumentException("targetType is required");
        }
        if (event.getMessage() == null || event.getMessage().isBlank()) {
            throw new IllegalArgumentException("message is required");
        }
    }
}
