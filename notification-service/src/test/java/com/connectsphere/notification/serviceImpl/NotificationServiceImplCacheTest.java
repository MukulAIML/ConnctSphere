package com.connectsphere.notification.serviceImpl;

import com.connectsphere.notification.repository.NotificationRepository;
import com.connectsphere.notification.websocket.NotificationWebSocketController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceImplCacheTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private JavaMailSender mailSender;

    @Mock
    private RedisTemplate<String, Long> redisTemplate;

    @Mock
    private ValueOperations<String, Long> valueOperations;

    @Mock
    private NotificationWebSocketController webSocketController;

    @InjectMocks
    private NotificationServiceImpl notificationService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(notificationService, "unreadCountTtlHours", 24L);
        ReflectionTestUtils.setField(notificationService, "unreadCountKeyPrefix", "notification:unread:");
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void getUnreadCount_returnsCachedValue_withoutDbCall() {
        Long recipientId = 10L;
        String expectedKey = "notification:unread:10";
        when(valueOperations.get(expectedKey)).thenReturn(7L);

        long count = notificationService.getUnreadCount(recipientId);

        assertEquals(7L, count);
        verify(valueOperations).get(expectedKey);
        verify(notificationRepository, never()).countByRecipientIdAndIsReadFalse(anyLong());
        verify(valueOperations, never()).set(anyString(), anyLong(), any(Duration.class));
    }

    @Test
    void getUnreadCount_onCacheMiss_queriesDb_andBackfillsRedis() {
        Long recipientId = 10L;
        String expectedKey = "notification:unread:10";
        when(valueOperations.get(expectedKey)).thenReturn(null);
        when(notificationRepository.countByRecipientIdAndIsReadFalse(recipientId)).thenReturn(3L);

        long count = notificationService.getUnreadCount(recipientId);

        assertEquals(3L, count);
        verify(notificationRepository).countByRecipientIdAndIsReadFalse(recipientId);
        verify(valueOperations).set(expectedKey, 3L, Duration.ofHours(24));
    }

    @Test
    void getUnreadCount_usesConfiguredKeyPrefix() {
        ReflectionTestUtils.setField(notificationService, "unreadCountKeyPrefix", "custom:unread:");
        Long recipientId = 33L;
        String expectedKey = "custom:unread:33";
        when(valueOperations.get(expectedKey)).thenReturn(5L);

        long count = notificationService.getUnreadCount(recipientId);

        assertEquals(5L, count);
        verify(valueOperations).get(expectedKey);
        verify(notificationRepository, never()).countByRecipientIdAndIsReadFalse(anyLong());
    }
}
