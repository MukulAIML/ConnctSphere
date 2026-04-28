package com.connectsphere.notification.repository;

import com.connectsphere.notification.entity.NotificationEntity;
import com.connectsphere.notification.entity.NotificationType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<NotificationEntity, Long> {

    List<NotificationEntity> findByRecipientIdOrderByCreatedAtDesc(Long recipientId);

    List<NotificationEntity> findByRecipientIdAndIsReadFalseOrderByCreatedAtDesc(Long recipientId);

    long countByRecipientIdAndIsReadFalse(Long recipientId);

    List<NotificationEntity> findByRecipientIdAndIsRead(Long recipientId, Boolean isRead);

    long countByRecipientIdAndIsRead(Long recipientId, Boolean isRead);

    List<NotificationEntity> findByType(NotificationType type);

    List<NotificationEntity> findByActorIdAndTargetId(Long actorId, Long targetId);

    void deleteByNotificationId(Long notificationId);

    @Modifying(clearAutomatically = true)
    @Transactional
    @Query(value = "UPDATE notifications SET is_read = true WHERE recipient_id = :recipientId AND is_read = false", nativeQuery = true)
    int markAllAsReadByRecipientId(@Param("recipientId") Long recipientId);
}
