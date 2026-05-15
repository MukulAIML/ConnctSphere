package com.connectsphere.post.controller;

import com.connectsphere.post.dto.CreateReportRequestDTO;
import com.connectsphere.post.dto.ReportResponseDTO;
import com.connectsphere.post.exception.UnauthorizedAccessException;
import com.connectsphere.post.service.ReportService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/posts/reports")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @PostMapping
    public ResponseEntity<ReportResponseDTO> submitReport(@Valid @RequestBody CreateReportRequestDTO request) {
        Long reporterId = requireAuthenticatedUserId();
        ReportResponseDTO created = reportService.submitReport(request, reporterId);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/mine")
    public ResponseEntity<List<ReportResponseDTO>> getMyReports() {
        Long reporterId = requireAuthenticatedUserId();
        return ResponseEntity.ok(reportService.getMyReports(reporterId));
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
