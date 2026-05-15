package com.connectsphere.commentservice.messaging;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class NotificationEventProducer {

	private static final Logger log = LoggerFactory.getLogger(NotificationEventProducer.class);

	private final RabbitTemplate rabbitTemplate;

	@Value("${rabbitmq.exchange}")
	private String exchange;

	@Value("${rabbitmq.routing.key}")
	private String routingKey;

	public NotificationEventProducer(RabbitTemplate rabbitTemplate) {
		this.rabbitTemplate = rabbitTemplate;
	}

	public void publish(Long recipientId, Long actorId, String type, String message, Long targetId, String targetType) {
		Map<String, Object> payload = Map.of(
				"recipientId", recipientId,
				"actorId", actorId,
				"type", type,
				"message", message,
				"targetId", targetId,
				"targetType", targetType
		);
		rabbitTemplate.convertAndSend(exchange, routingKey, payload);
		log.debug("Published notification event: type={}, recipientId={}, actorId={}, targetId={}",
				type, recipientId, actorId, targetId);
	}
}
