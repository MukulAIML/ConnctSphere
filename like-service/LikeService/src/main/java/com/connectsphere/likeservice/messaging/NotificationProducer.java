package com.connectsphere.likeservice.messaging;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.connectsphere.likeservice.dto.CreateNotificationRequest;

import java.util.Map;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class NotificationProducer {
	private final RabbitTemplate rabbitTemplate;

	@Value("${rabbitmq.exchange}")
	private String exchange;

	@Value("${rabbitmq.routing.key}")
	private String routingKey;

	public void sendNotification(CreateNotificationRequest request) {
		Map<String, Object> payload = Map.of(
				"recipientId", request.getRecipientId(),
				"actorId", request.getActorId(),
				"type", request.getType(),
				"message", request.getMessage(),
				"targetId", request.getTargetId(),
				"targetType", request.getTargetType()
		);
		log.debug("Sending notification event payload: {}", payload);
		rabbitTemplate.convertAndSend(exchange, routingKey, payload);
	}
}
