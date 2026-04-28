package com.connectsphere.notification.controller;

import com.connectsphere.notification.dto.ApiResponse;
import com.connectsphere.notification.dto.BulkNotificationRequestDTO;
import com.connectsphere.notification.dto.NotificationRequestDTO;
import com.connectsphere.notification.dto.NotificationResponseDTO;
import com.connectsphere.notification.service.NotificationService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/notifications")
public class NotificationController {

    private static final Logger logger = LoggerFactory.getLogger(NotificationController.class);

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    private Long getAuthenticatedUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() != null) {
            try {
                return Long.valueOf(authentication.getPrincipal().toString());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<NotificationResponseDTO>> createNotification(@Valid @RequestBody NotificationRequestDTO requestDTO) {
        logger.info("Received request to create notification");
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created("Notification created successfully", notificationService.createNotification(requestDTO)));
    }

    @PostMapping("/bulk")
    public ResponseEntity<ApiResponse<Void>> sendBulkNotification(@Valid @RequestBody BulkNotificationRequestDTO bulkRequestDTO) {
        logger.info("Received request to send bulk notification");
        notificationService.sendBulkNotification(bulkRequestDTO);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created("Bulk notification sent successfully", null));
    }

    @PostMapping("/send-bulk")
    public ResponseEntity<ApiResponse<Void>> sendBulkNotificationAlias(@Valid @RequestBody BulkNotificationRequestDTO bulkRequestDTO) {
        return sendBulkNotification(bulkRequestDTO);
    }

    @GetMapping("/all")
    public ResponseEntity<ApiResponse<List<NotificationResponseDTO>>> getAllNotifications() {
        logger.info("Received request to fetch all notifications");
        return ResponseEntity.ok(ApiResponse.success("All notifications fetched successfully", notificationService.getAllNotifications()));
    }

    @GetMapping("/user/{recipientId}")
    public ResponseEntity<ApiResponse<List<NotificationResponseDTO>>> getNotificationsByRecipientId(@PathVariable Long recipientId) {
        logger.info("Received request to fetch notifications for user: {}", recipientId);
        return ResponseEntity.ok(ApiResponse.success("Notifications fetched successfully", notificationService.getNotificationsByRecipientId(recipientId)));
    }

    @GetMapping("/recipient/{recipientId}")
    public ResponseEntity<ApiResponse<List<NotificationResponseDTO>>> getNotificationsByRecipientAlias(@PathVariable Long recipientId) {
        return getNotificationsByRecipientId(recipientId);
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<NotificationResponseDTO>>> getMyNotifications() {
        Long recipientId = getAuthenticatedUserId();
        logger.info("Received request to fetch notifications for authenticated user: {}", recipientId);
        if (recipientId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error(HttpStatus.UNAUTHORIZED.value(), "User not authenticated"));
        }
        return ResponseEntity.ok(ApiResponse.success("Notifications fetched successfully", notificationService.getNotificationsByRecipientId(recipientId)));
    }

    @GetMapping("/unread/{recipientId}")
    public ResponseEntity<ApiResponse<List<NotificationResponseDTO>>> getUnreadNotifications(@PathVariable Long recipientId) {
        logger.info("Received request to fetch unread notifications for user: {}", recipientId);
        return ResponseEntity.ok(ApiResponse.success("Unread notifications fetched successfully", notificationService.getUnreadNotifications(recipientId)));
    }

    @GetMapping("/unread")
    public ResponseEntity<ApiResponse<List<NotificationResponseDTO>>> getMyUnreadNotifications() {
        Long recipientId = getAuthenticatedUserId();
        logger.info("Received request to fetch unread notifications for authenticated user: {}", recipientId);
        if (recipientId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error(HttpStatus.UNAUTHORIZED.value(), "User not authenticated"));
        }
        return ResponseEntity.ok(ApiResponse.success("Unread notifications fetched successfully", notificationService.getUnreadNotifications(recipientId)));
    }

    @GetMapping("/unread/count/{recipientId}")
    public ResponseEntity<ApiResponse<Long>> getUnreadCount(@PathVariable Long recipientId) {
        logger.info("Received request to fetch unread notification count for user: {}", recipientId);
        return ResponseEntity.ok(ApiResponse.success("Unread count fetched successfully", notificationService.getUnreadCount(recipientId)));
    }

    @GetMapping("/unread/count")
    public ResponseEntity<ApiResponse<Long>> getMyUnreadCount() {
        Long recipientId = getAuthenticatedUserId();
        logger.info("Received request to fetch unread count for authenticated user: {}", recipientId);
        if (recipientId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error(HttpStatus.UNAUTHORIZED.value(), "User not authenticated"));
        }
        return ResponseEntity.ok(ApiResponse.success("Unread count fetched successfully", notificationService.getUnreadCount(recipientId)));
    }

    @GetMapping("/unreadCount")
    public ResponseEntity<ApiResponse<Long>> getUnreadCountCamelCase(@RequestParam(required = false) Long recipientId) {
        Long resolvedRecipientId = recipientId != null ? recipientId : getAuthenticatedUserId();
        if (resolvedRecipientId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error(HttpStatus.UNAUTHORIZED.value(), "User not authenticated"));
        }
        return ResponseEntity.ok(ApiResponse.success("Unread count fetched successfully", notificationService.getUnreadCount(resolvedRecipientId)));
    }

    // Explicitly added to match frontend requirement
    @GetMapping("/{recipientId}")
    public ResponseEntity<ApiResponse<List<NotificationResponseDTO>>> getNotificationsDirect(@PathVariable Long recipientId) {
        return getNotificationsByRecipientId(recipientId);
    }

    @GetMapping("/unread-count/{recipientId}")
    public ResponseEntity<ApiResponse<Long>> getUnreadCountDirect(@PathVariable Long recipientId) {
        return getUnreadCount(recipientId);
    }

    @PutMapping("/{id}/read")
    public ResponseEntity<ApiResponse<NotificationResponseDTO>> markAsRead(@PathVariable("id") Long id) {
        Long recipientId = getAuthenticatedUserId();
        if (recipientId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error(HttpStatus.UNAUTHORIZED.value(), "User not authenticated"));
        }
        logger.info("Received request to mark notification {} as read by user: {}", id, recipientId);
        return ResponseEntity.ok(ApiResponse.success("Notification marked as read", notificationService.markAsRead(id, recipientId)));
    }

    @PutMapping("/read/{id}")
    public ResponseEntity<ApiResponse<NotificationResponseDTO>> markAsReadAlias(@PathVariable("id") Long id) {
        return markAsRead(id);
    }

    @PutMapping("/read-all/user/{recipientId}")
    public ResponseEntity<ApiResponse<Void>> markAllAsRead(@PathVariable("recipientId") Long recipientId) {
        Long authenticatedUserId = getAuthenticatedUserId();
        if (authenticatedUserId == null || !recipientId.equals(authenticatedUserId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error(HttpStatus.FORBIDDEN.value(), "Forbidden: You can only update your own notifications"));
        }
        logger.info("Received request to mark all notifications as read for user: {}", recipientId);
        notificationService.markAllAsRead(recipientId);
        return ResponseEntity.ok(ApiResponse.success("All notifications marked as read", null));
    }

    @PutMapping("/read-all")
    public ResponseEntity<ApiResponse<Void>> markMyAllAsRead() {
        Long recipientId = getAuthenticatedUserId();
        logger.info("Received request to mark all notifications as read for authenticated user: {}", recipientId);
        if (recipientId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error(HttpStatus.UNAUTHORIZED.value(), "User not authenticated"));
        }
        notificationService.markAllAsRead(recipientId);
        return ResponseEntity.ok(ApiResponse.success("All notifications marked as read", null));
    }

    @PutMapping("/readAll")
    public ResponseEntity<ApiResponse<Void>> markMyAllAsReadAlias() {
        return markMyAllAsRead();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteNotification(@PathVariable Long id) {
        Long recipientId = getAuthenticatedUserId();
        logger.info("Received request to delete notification {} by user: {}", id, recipientId);
        notificationService.deleteNotification(id, recipientId);
        return ResponseEntity.ok(ApiResponse.success("Notification deleted successfully", null));
    }
}
