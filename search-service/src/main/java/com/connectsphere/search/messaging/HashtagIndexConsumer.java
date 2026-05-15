package com.connectsphere.search.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.connectsphere.search.dto.IndexRequestDTO;
import com.connectsphere.search.service.SearchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class HashtagIndexConsumer {

    private static final Logger logger = LoggerFactory.getLogger(HashtagIndexConsumer.class);

    private final SearchService searchService;
    private final ObjectMapper objectMapper;

    public HashtagIndexConsumer(SearchService searchService, ObjectMapper objectMapper) {
        this.searchService = searchService;
        this.objectMapper = objectMapper;
    }

    @RabbitListener(queues = "${app.rabbitmq.queue.hashtag-index}")
    public void handleHashtagIndexEvent(Object payload) {
        try {
            IndexRequestDTO requestDTO = normalizePayload(payload);
            validatePayload(requestDTO);
            logger.info("[RabbitMQ] Received hashtag index event for postId={}", requestDTO.getPostId());
            searchService.indexPost(requestDTO);
            logger.info("[RabbitMQ] Successfully indexed postId={}", requestDTO.getPostId());
        } catch (IllegalArgumentException e) {
            logger.warn("[RabbitMQ] Discarding invalid hashtag index event: {}", e.getMessage());
        } catch (Exception e) {
            logger.error("[RabbitMQ] Failed to index hashtag event: {}", e.getMessage(), e);
            throw e; // re-throw so RabbitMQ can handle retry / DLQ
        }
    }

    private IndexRequestDTO normalizePayload(Object payload) {
        if (payload instanceof Message message) {
            return normalizePayload(readMessageBody(message));
        }
        if (payload instanceof byte[] bytes) {
            return normalizePayload(readJsonBody(bytes));
        }
        if (payload instanceof IndexRequestDTO dto) {
            return dto;
        }
        if (payload instanceof Map<?, ?> map) {
            return IndexRequestDTO.builder()
                    .postId(asLong(map.get("postId")))
                    .content(asString(map.get("content")))
                    .build();
        }
        throw new IllegalArgumentException("Unsupported hashtag index payload type: "
                + (payload == null ? "null" : payload.getClass().getName()));
    }

    private Object readMessageBody(Message message) {
        if (message == null || message.getBody() == null || message.getBody().length == 0) {
            throw new IllegalArgumentException("Empty RabbitMQ message body");
        }
        return readJsonBody(message.getBody());
    }

    private Object readJsonBody(byte[] body) {
        try {
            return objectMapper.readValue(body, Object.class);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Invalid JSON payload for hashtag index event");
        }
    }

    private void validatePayload(IndexRequestDTO dto) {
        if (dto.getPostId() == null || dto.getPostId() <= 0) {
            throw new IllegalArgumentException("postId must be greater than 0");
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
}
