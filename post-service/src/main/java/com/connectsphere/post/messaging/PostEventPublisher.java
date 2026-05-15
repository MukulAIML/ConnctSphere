package com.connectsphere.post.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class PostEventPublisher {

    private static final Logger logger = LoggerFactory.getLogger(PostEventPublisher.class);

    private final RabbitTemplate rabbitTemplate;

    @Value("${app.rabbitmq.exchange}")
    private String exchange;

    @Value("${app.rabbitmq.routing-key.notification-dispatch}")
    private String notificationDispatchRoutingKey;

    @Value("${app.rabbitmq.routing-key.hashtag-index}")
    private String hashtagIndexRoutingKey;

    public PostEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publishMentionNotification(Long recipientId,
                                           Long actorId,
                                           Long postId,
                                           String message) {
        Map<String, Object> payload = Map.of(
                "recipientId", recipientId,
                "actorId", actorId,
                "type", "MENTION",
                "message", message,
                "targetId", postId,
                "targetType", "POST"
        );
        rabbitTemplate.convertAndSend(exchange, notificationDispatchRoutingKey, payload);
        logger.debug("Published mention notification event: recipientId={}, actorId={}, postId={}",
                recipientId, actorId, postId);
    }

    public void publishHashtagIndex(Long postId, String content) {
        Map<String, Object> payload = Map.of(
                "postId", postId,
                "content", content
        );
        rabbitTemplate.convertAndSend(exchange, hashtagIndexRoutingKey, payload);
        logger.debug("Published hashtag index event for postId={}", postId);
    }
}
