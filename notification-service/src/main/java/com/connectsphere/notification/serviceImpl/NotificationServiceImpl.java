package com.connectsphere.notification.serviceImpl;

import com.connectsphere.notification.dto.BulkNotificationRequestDTO;
import com.connectsphere.notification.dto.NotificationRequestDTO;
import com.connectsphere.notification.dto.NotificationResponseDTO;
import com.connectsphere.notification.entity.NotificationEntity;
import com.connectsphere.notification.entity.NotificationType;
import com.connectsphere.notification.exception.ResourceNotFoundException;
import com.connectsphere.notification.exception.UnauthorizedAccessException;
import com.connectsphere.notification.repository.NotificationRepository;
import com.connectsphere.notification.service.NotificationService;
import com.connectsphere.notification.websocket.NotificationWebSocketController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class NotificationServiceImpl implements NotificationService {

    private static final Logger logger = LoggerFactory.getLogger(NotificationServiceImpl.class);

    private final NotificationRepository notificationRepository;
    private final JavaMailSender mailSender;
    private final RedisTemplate<String, Long> redisTemplate;
    private final NotificationWebSocketController webSocketController;

    @Value("${app.notification.email.from}")
    private String emailFrom;

    @Value("${app.notification.email.enabled:false}")
    private boolean emailEnabled;

    @Value("${app.redis.unread-count-ttl-hours:24}")
    private long unreadCountTtlHours;

    @Value("${app.redis.unread-count-prefix:notification:unread:}")
    private String unreadCountKeyPrefix;

    public NotificationServiceImpl(
            NotificationRepository notificationRepository,
            JavaMailSender mailSender,
            RedisTemplate<String, Long> redisTemplate,
            NotificationWebSocketController webSocketController) {
        this.notificationRepository = notificationRepository;
        this.mailSender = mailSender;
        this.redisTemplate = redisTemplate;
        this.webSocketController = webSocketController;
    }

    @Override
    public NotificationResponseDTO createNotification(NotificationRequestDTO requestDTO) {
        NotificationEntity notification = NotificationEntity.builder()
                .recipientId(requestDTO.getRecipientId())
                .actorId(requestDTO.getActorId())
                .type(requestDTO.getType())
                .message(requestDTO.getMessage())
                .targetId(requestDTO.getTargetId())
                .targetType(requestDTO.getTargetType())
                .isRead(false)
                .build();

        NotificationEntity saved = notificationRepository.save(notification);
        NotificationResponseDTO responseDTO = mapToDTO(saved);

        // Increment Redis unread counter
        incrementRedisUnreadCount(saved.getRecipientId());

        // Push via WebSocket
        webSocketController.pushNotificationToUser(saved.getRecipientId(), responseDTO);

        // Email alert for high-priority events
        if (isHighPriority(saved.getType())) {
            sendEmailAlert(responseDTO);
        }

        return responseDTO;
    }

    @Override
    @Transactional
    public void sendBulkNotification(BulkNotificationRequestDTO bulkRequestDTO) {
        List<NotificationEntity> notifications = bulkRequestDTO.getRecipientIds().stream()
                .map(recipientId -> NotificationEntity.builder()
                        .recipientId(recipientId)
                        .actorId(bulkRequestDTO.getActorId())
                        .type(bulkRequestDTO.getType())
                        .message(bulkRequestDTO.getMessage())
                        .targetId(bulkRequestDTO.getTargetId())
                        .targetType(bulkRequestDTO.getTargetType())
                        .isRead(false)
                        .build())
                .collect(Collectors.toList());

        List<NotificationEntity> saved = notificationRepository.saveAll(notifications);

        saved.forEach(n -> {
            incrementRedisUnreadCount(n.getRecipientId());
            NotificationResponseDTO dto = mapToDTO(n);
            webSocketController.pushNotificationToUser(n.getRecipientId(), dto);
        });
    }

    @Override
    public List<NotificationResponseDTO> getAllNotifications() {
        return notificationRepository.findAll().stream()
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    @Override
    public List<NotificationResponseDTO> getNotificationsByRecipientId(Long recipientId) {
        return notificationRepository.findByRecipientIdOrderByCreatedAtDesc(recipientId)
                .stream().map(this::mapToDTO).collect(Collectors.toList());
    }

    @Override
    public List<NotificationResponseDTO> getUnreadNotifications(Long recipientId) {
        return notificationRepository.findByRecipientIdAndIsReadFalseOrderByCreatedAtDesc(recipientId)
                .stream().map(this::mapToDTO).collect(Collectors.toList());
    }

    /**
     * Returns unread count from Redis. Falls back to MySQL if key is absent.
     */
    @Override
    public long getUnreadCount(Long recipientId) {
        String key = unreadCountKey(recipientId);
        try {
            Long cached = redisTemplate.opsForValue().get(key);
            if (cached != null) {
                logger.debug("Redis cache hit for unread count, userId={}", recipientId);
                return cached;
            }
        } catch (Exception e) {
            logger.warn("Redis unavailable, falling back to DB for unread count: {}", e.getMessage());
        }

        // Fallback: query DB and re-cache
        long count = notificationRepository.countByRecipientIdAndIsReadFalse(recipientId);
        try {
            redisTemplate.opsForValue().set(key, count, Duration.ofHours(unreadCountTtlHours));
        } catch (Exception ignored) {
        }
        return count;
    }

    @Override
    public NotificationResponseDTO markAsRead(Long notificationId, Long recipientId) {
        NotificationEntity notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found"));

        if (!notification.getRecipientId().equals(recipientId)) {
            throw new UnauthorizedAccessException("You can only update your own notifications");
        }

        if (!notification.getIsRead()) {
            notification.setIsRead(true);
            notificationRepository.save(notification);
            decrementRedisUnreadCount(recipientId);
        }
        return mapToDTO(notification);
    }

    @Override
    @Transactional
    public void markAllAsRead(Long recipientId) {
        logger.info("Marking all notifications as read for user: {}", recipientId);
        int updated = notificationRepository.markAllAsReadByRecipientId(recipientId);
        logger.info("Marked {} notifications as read for user: {}", updated, recipientId);
        // Reset Redis counter to 0
        try {
            redisTemplate.opsForValue().set(unreadCountKey(recipientId), 0L,
                    Duration.ofHours(unreadCountTtlHours));
        } catch (Exception e) {
            logger.warn("Could not reset Redis unread count: {}", e.getMessage());
        }
    }

    @Override
    public void deleteNotification(Long notificationId, Long recipientId) {
        NotificationEntity notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found"));

        if (!notification.getRecipientId().equals(recipientId)) {
            throw new UnauthorizedAccessException("You can only delete your own notifications");
        }

        if (!notification.getIsRead()) {
            decrementRedisUnreadCount(recipientId);
        }

        notificationRepository.delete(notification);
    }

    /**
     * Sends an actual email via JavaMailSender (SMTP / AWS SES).
     * Enabled through app.notification.email.enabled=true.
     */
    @Override
    public void sendEmailAlert(NotificationResponseDTO notification) {
        if (!emailEnabled) {
            logger.info("Email alerts disabled. Skipping email for notificationId={}", notification.getNotificationId());
            return;
        }
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(emailFrom);
            // Recipient email would ideally be resolved from auth-service;
            // as a safe default we use a placeholder unless the caller provided it.
            String toAddress = resolveRecipientEmail(notification.getRecipientId());
            message.setTo(toAddress);
            message.setSubject("[ConnectSphere] New " + notification.getType() + " notification");
            message.setText("Hi,\n\nYou have a new notification:\n\n" + notification.getMessage()
                    + "\n\nLog in to ConnectSphere to view it.\n\nTeam ConnectSphere");
            mailSender.send(message);
            logger.info("Email alert sent for notificationId={} to {}", notification.getNotificationId(), toAddress);
        } catch (Exception e) {
            logger.error("Failed to send email for notificationId={}: {}", notification.getNotificationId(), e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private boolean isHighPriority(NotificationType type) {
        return type == NotificationType.FOLLOW || type == NotificationType.MENTION;
    }

    private void incrementRedisUnreadCount(Long recipientId) {
        try {
            String key = unreadCountKey(recipientId);
            redisTemplate.opsForValue().increment(key);
            redisTemplate.expire(key, Duration.ofHours(unreadCountTtlHours));
        } catch (Exception e) {
            logger.warn("Failed to increment Redis unread count for userId={}: {}", recipientId, e.getMessage());
        }
    }

    private void decrementRedisUnreadCount(Long recipientId) {
        try {
            String key = unreadCountKey(recipientId);
            Long current = redisTemplate.opsForValue().get(key);
            if (current != null && current > 0) {
                redisTemplate.opsForValue().decrement(key);
            }
        } catch (Exception e) {
            logger.warn("Failed to decrement Redis unread count for userId={}: {}", recipientId, e.getMessage());
        }
    }

    private String unreadCountKey(Long recipientId) {
        String prefix = (unreadCountKeyPrefix == null || unreadCountKeyPrefix.isBlank())
                ? "notification:unread:"
                : unreadCountKeyPrefix;
        return prefix + recipientId;
    }

    /**
     * Placeholder: in a full implementation this would call auth-service to look up the user's email.
     */
    private String resolveRecipientEmail(Long recipientId) {
        return "user" + recipientId + "@connectsphere.internal";
    }

    private NotificationResponseDTO mapToDTO(NotificationEntity notification) {
        return NotificationResponseDTO.builder()
                .notificationId(notification.getNotificationId())
                .recipientId(notification.getRecipientId())
                .actorId(notification.getActorId())
                .type(notification.getType())
                .message(notification.getMessage())
                .targetId(notification.getTargetId())
                .targetType(notification.getTargetType())
                .isRead(notification.getIsRead())
                .createdAt(notification.getCreatedAt())
                .build();
    }
}
