package com.connectsphere.post.controller;

import com.connectsphere.post.dto.ReportResponseDTO;
import com.connectsphere.post.dto.ResolveReportRequestDTO;
import com.connectsphere.post.entity.ReportStatus;
import com.connectsphere.post.entity.ReportTargetType;
import com.connectsphere.post.exception.UnauthorizedAccessException;
import com.connectsphere.post.service.ReportService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/posts/admin/reports")
public class AdminReportController {

    private final ReportService reportService;

    public AdminReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping
    public ResponseEntity<List<ReportResponseDTO>> getReports(
            @RequestParam(required = false) ReportStatus status,
            @RequestParam(required = false) ReportTargetType targetType) {
        return ResponseEntity.ok(reportService.getReportsForModeration(status, targetType));
    }

    @GetMapping("/{reportId}")
    public ResponseEntity<ReportResponseDTO> getReport(@PathVariable Long reportId) {
        return ResponseEntity.ok(reportService.getReportById(reportId));
    }

    @PatchMapping("/{reportId}/status")
    public ResponseEntity<ReportResponseDTO> updateReportStatus(
            @PathVariable Long reportId,
            @Valid @RequestBody ResolveReportRequestDTO request) {

        Long moderatorId = requireAuthenticatedUserId();
        String role = resolvePrimaryRole();
        return ResponseEntity.ok(reportService.updateReportStatus(reportId, request, moderatorId, role));
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

    private String resolvePrimaryRole() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getAuthorities() == null || auth.getAuthorities().isEmpty()) {
            return "ROLE_USER";
        }
        return auth.getAuthorities()
                .stream()
                .map(GrantedAuthority::getAuthority)
                .filter(role -> role != null && !role.isBlank())
                .findFirst()
                .orElse("ROLE_USER");
    }
}
