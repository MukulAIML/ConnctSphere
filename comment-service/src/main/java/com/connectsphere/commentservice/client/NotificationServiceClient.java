package com.connectsphere.commentservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import com.connectsphere.commentservice.dto.CreateNotificationRequest;

@FeignClient(name = "notification-service")
public interface NotificationServiceClient {
    
    @PostMapping("/notifications")
    void createNotification(@RequestBody CreateNotificationRequest request);
}
