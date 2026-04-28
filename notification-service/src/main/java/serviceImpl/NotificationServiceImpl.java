package com.connectsphere.notification.serviceImpl;

import com.connectsphere.notification.dto.BulkNotificationRequestDTO;
import com.connectsphere.notification.dto.NotificationRequestDTO;
import com.connectsphere.notification.dto.NotificationResponseDTO;
import com.connectsphere.notification.entity.NotificationEntity;
import com.connectsphere.notification.exception.ResourceNotFoundException;
import com.connectsphere.notification.exception.UnauthorizedAccessException;
import com.connectsphere.notification.repository.NotificationRepository;
import com.connectsphere.notification.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class NotificationServiceImpl implements NotificationService {

    private static final Logger logger = LoggerFactory.getLogger(NotificationServiceImpl.class);

    private final NotificationRepository notificationRepository;

    public NotificationServiceImpl(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
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

        NotificationEntity savedNotification = notificationRepository.save(notification);
        NotificationResponseDTO responseDTO = mapToDTO(savedNotification);
        if (savedNotification.getType() == com.connectsphere.notification.entity.NotificationType.FOLLOW) {
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

        notificationRepository.saveAll(notifications);
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
        List<NotificationEntity> notifications = notificationRepository.findByRecipientIdOrderByCreatedAtDesc(recipientId);
        return notifications.stream().map(this::mapToDTO).collect(Collectors.toList());
    }

    @Override
    public List<NotificationResponseDTO> getUnreadNotifications(Long recipientId) {
        List<NotificationEntity> notifications = notificationRepository.findByRecipientIdAndIsReadFalseOrderByCreatedAtDesc(recipientId);
        return notifications.stream().map(this::mapToDTO).collect(Collectors.toList());
    }

    @Override
    public long getUnreadCount(Long recipientId) {
        return notificationRepository.countByRecipientIdAndIsReadFalse(recipientId);
    }

    @Override
    public NotificationResponseDTO markAsRead(Long notificationId, Long recipientId) {
        NotificationEntity notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found"));

        if (!notification.getRecipientId().equals(recipientId)) {
            throw new UnauthorizedAccessException("You can only update your own notifications");
        }

        notification.setIsRead(true);
        NotificationEntity updatedNotification = notificationRepository.save(notification);
        return mapToDTO(updatedNotification);
    }

    @Override
    @Transactional
    public void markAllAsRead(Long recipientId) {
        logger.info("Marking all notifications as read for user: {}", recipientId);
        int updatedCount = notificationRepository.markAllAsReadByRecipientId(recipientId);
        logger.info("Successfully marked {} notifications as read for user: {}", updatedCount, recipientId);
    }

    @Override
    public void deleteNotification(Long notificationId, Long recipientId) {
        NotificationEntity notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found"));

        if (!notification.getRecipientId().equals(recipientId)) {
            throw new UnauthorizedAccessException("You can only delete your own notifications");
        }

        notificationRepository.delete(notification);
    }

    @Override
    public void sendEmailAlert(NotificationResponseDTO notification) {
        // Placeholder hook for SMTP provider integration.
        logger.info("Email alert hook executed for notificationId={} type={}", notification.getNotificationId(), notification.getType());
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
