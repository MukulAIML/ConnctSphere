package com.connectsphere.post.controller;

import com.connectsphere.post.dto.AdminBroadcastRequestDTO;
import com.connectsphere.post.dto.AdminBroadcastResponseDTO;
import com.connectsphere.post.dto.AdminPlatformStatsDTO;
import com.connectsphere.post.exception.UnauthorizedAccessException;
import com.connectsphere.post.service.AdminDashboardService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/posts/admin/dashboard")
public class AdminDashboardController {

    private final AdminDashboardService adminDashboardService;

    public AdminDashboardController(AdminDashboardService adminDashboardService) {
        this.adminDashboardService = adminDashboardService;
    }

    @GetMapping("/stats")
    public ResponseEntity<AdminPlatformStatsDTO> getPlatformStats() {
        return ResponseEntity.ok(adminDashboardService.getPlatformStats());
    }

    @PostMapping("/broadcast")
    public ResponseEntity<AdminBroadcastResponseDTO> sendBroadcast(
            @Valid @RequestBody AdminBroadcastRequestDTO request) {
        Long actorId = requireAuthenticatedUserId();
        return ResponseEntity.ok(adminDashboardService.sendBroadcast(request, actorId));
    }

    private Long requireAuthenticatedUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() != null) {
            try {
                return Long.valueOf(auth.getPrincipal().toString());
            } catch (NumberFormatException ignored) {
            }
        }
        throw new UnauthorizedAccessException("User not authenticated");
    }
}
