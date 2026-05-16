package com.connectsphere.follow.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class FollowEventPublisher {

    private static final Logger logger = LoggerFactory.getLogger(FollowEventPublisher.class);

    private final RabbitTemplate rabbitTemplate;

    @Value("${app.rabbitmq.exchange}")
    private String exchange;

    @Value("${app.rabbitmq.routing-key.notification-dispatch}")
    private String notificationDispatchRoutingKey;

    public FollowEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publishFollowNotification(Long recipientId, Long actorId) {
        Map<String, Object> payload = Map.of(
                "recipientId", recipientId,
                "actorId", actorId,
                "type", "FOLLOW",
                "message", "Someone started following you",
                "targetId", actorId,
                "targetType", "USER"
        );

        rabbitTemplate.convertAndSend(exchange, notificationDispatchRoutingKey, payload);
        logger.debug("Published follow notification event: recipientId={}, actorId={}", recipientId, actorId);
    }
}
