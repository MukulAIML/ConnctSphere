package com.connectsphere.post.service;

import com.connectsphere.post.dto.CreateReportRequestDTO;
import com.connectsphere.post.dto.ReportResponseDTO;
import com.connectsphere.post.dto.ResolveReportRequestDTO;
import com.connectsphere.post.entity.ReportStatus;
import com.connectsphere.post.entity.ReportTargetType;

import java.util.List;

public interface ReportService {

    ReportResponseDTO submitReport(CreateReportRequestDTO request, Long reporterId);

    List<ReportResponseDTO> getMyReports(Long reporterId);

    List<ReportResponseDTO> getReportsForModeration(ReportStatus status, ReportTargetType targetType);

    ReportResponseDTO getReportById(Long reportId);

    ReportResponseDTO updateReportStatus(Long reportId, ResolveReportRequestDTO request, Long moderatorId, String moderatorRole);
}
