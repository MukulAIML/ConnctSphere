package com.connectsphere.search.messaging;

import com.connectsphere.search.dto.IndexRequestDTO;
import com.connectsphere.search.service.SearchService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class HashtagIndexConsumerTest {

    @Mock
    private SearchService searchService;

    private HashtagIndexConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new HashtagIndexConsumer(searchService, new ObjectMapper());
    }

    @Test
    void shouldIndexWhenPayloadComesAsRawAmqpMessage() {
        String json = "{\"postId\":42,\"content\":\"Testing #connectsphere\"}";
        Message message = new Message(json.getBytes(StandardCharsets.UTF_8), new MessageProperties());

        consumer.handleHashtagIndexEvent(message);

        ArgumentCaptor<IndexRequestDTO> dtoCaptor = ArgumentCaptor.forClass(IndexRequestDTO.class);
        verify(searchService).indexPost(dtoCaptor.capture());

        IndexRequestDTO captured = dtoCaptor.getValue();
        assertEquals(42L, captured.getPostId());
        assertEquals("Testing #connectsphere", captured.getContent());
    }

    @Test
    void shouldDiscardInvalidJsonMessagePayload() {
        Message message = new Message("not-json".getBytes(StandardCharsets.UTF_8), new MessageProperties());

        consumer.handleHashtagIndexEvent(message);

        verify(searchService, never()).indexPost(org.mockito.ArgumentMatchers.any(IndexRequestDTO.class));
    }
}
