package com.connectsphere.notification.service;

import com.connectsphere.notification.dto.BulkNotificationRequestDTO;
import com.connectsphere.notification.dto.NotificationRequestDTO;
import com.connectsphere.notification.dto.NotificationResponseDTO;

import java.util.List;

public interface NotificationService {
    NotificationResponseDTO createNotification(NotificationRequestDTO requestDTO);
    void sendBulkNotification(BulkNotificationRequestDTO bulkRequestDTO);
    List<NotificationResponseDTO> getAllNotifications();
    List<NotificationResponseDTO> getNotificationsByRecipientId(Long recipientId);
    List<NotificationResponseDTO> getUnreadNotifications(Long recipientId);
    long getUnreadCount(Long recipientId);
    NotificationResponseDTO markAsRead(Long notificationId, Long recipientId);
    void markAllAsRead(Long recipientId);
    void deleteNotification(Long notificationId, Long recipientId);
    void sendEmailAlert(NotificationResponseDTO notification);
}
