package com.connectsphere.post.service;

import com.connectsphere.post.dto.AdminBroadcastRequestDTO;
import com.connectsphere.post.dto.AdminBroadcastResponseDTO;
import com.connectsphere.post.dto.AdminPlatformStatsDTO;

public interface AdminDashboardService {

    AdminPlatformStatsDTO getPlatformStats();

    AdminBroadcastResponseDTO sendBroadcast(AdminBroadcastRequestDTO request, Long actorId);
}
