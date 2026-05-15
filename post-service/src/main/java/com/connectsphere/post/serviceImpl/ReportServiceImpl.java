package com.connectsphere.post.serviceImpl;

import com.connectsphere.post.dto.CreateReportRequestDTO;
import com.connectsphere.post.dto.ReportResponseDTO;
import com.connectsphere.post.dto.ResolveReportRequestDTO;
import com.connectsphere.post.entity.Report;
import com.connectsphere.post.entity.ReportResolutionAction;
import com.connectsphere.post.entity.ReportStatus;
import com.connectsphere.post.entity.ReportTargetType;
import com.connectsphere.post.exception.BadRequestException;
import com.connectsphere.post.exception.ResourceNotFoundException;
import com.connectsphere.post.exception.UnauthorizedAccessException;
import com.connectsphere.post.repository.PostRepository;
import com.connectsphere.post.repository.ReportRepository;
import com.connectsphere.post.service.PostService;
import com.connectsphere.post.service.ReportService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ReportServiceImpl implements ReportService {

    private final ReportRepository reportRepository;
    private final PostRepository postRepository;
    private final PostService postService;
    private final RestTemplate restTemplate;

    @Value("${comment-service.url}")
    private String commentServiceUrl;

    @Value("${auth-service.url}")
    private String authServiceUrl;

    public ReportServiceImpl(ReportRepository reportRepository,
                             PostRepository postRepository,
                             PostService postService,
                             RestTemplate restTemplate) {
        this.reportRepository = reportRepository;
        this.postRepository = postRepository;
        this.postService = postService;
        this.restTemplate = restTemplate;
    }

    @Override
    @Transactional
    public ReportResponseDTO submitReport(CreateReportRequestDTO request, Long reporterId) {
        validateTargetExists(request.getTargetType(), request.getTargetId());

        boolean duplicateOpenReport = reportRepository.existsByReporterIdAndTargetTypeAndTargetIdAndStatusIn(
                reporterId,
                request.getTargetType(),
                request.getTargetId(),
                List.of(ReportStatus.OPEN, ReportStatus.UNDER_REVIEW)
        );

        if (duplicateOpenReport) {
            throw new BadRequestException("You already have an active report for this content");
        }

        Report report = Report.builder()
                .reporterId(reporterId)
                .targetType(request.getTargetType())
                .targetId(request.getTargetId())
                .reason(request.getReason())
                .details(trimToNull(request.getDetails()))
                .status(ReportStatus.OPEN)
                .resolutionAction(ReportResolutionAction.NO_ACTION)
                .build();

        return mapToDTO(reportRepository.save(report));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReportResponseDTO> getMyReports(Long reporterId) {
        return reportRepository.findByReporterIdOrderByCreatedAtDesc(reporterId)
                .stream()
                .map(this::mapToDTO)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReportResponseDTO> getReportsForModeration(ReportStatus status, ReportTargetType targetType) {
        List<Report> reports;

        if (status != null && targetType != null) {
            reports = reportRepository.findByStatusAndTargetTypeOrderByCreatedAtDesc(status, targetType);
        } else if (status != null) {
            reports = reportRepository.findByStatusOrderByCreatedAtDesc(status);
        } else if (targetType != null) {
            reports = reportRepository.findByTargetTypeOrderByCreatedAtDesc(targetType);
        } else {
            reports = reportRepository.findAll()
                    .stream()
                    .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                    .toList();
        }

        return reports.stream().map(this::mapToDTO).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ReportResponseDTO getReportById(Long reportId) {
        return mapToDTO(getReportEntity(reportId));
    }

    @Override
    @Transactional
    public ReportResponseDTO updateReportStatus(Long reportId,
                                                ResolveReportRequestDTO request,
                                                Long moderatorId,
                                                String moderatorRole) {
        Report report = getReportEntity(reportId);
        ReportStatus nextStatus = request.getStatus();
        ReportResolutionAction action = request.getResolutionAction() == null
                ? ReportResolutionAction.NO_ACTION
                : request.getResolutionAction();

        if (nextStatus == ReportStatus.OPEN) {
            throw new BadRequestException("Reports cannot be moved back to OPEN");
        }

        if (nextStatus == ReportStatus.UNDER_REVIEW && action != ReportResolutionAction.NO_ACTION) {
            throw new BadRequestException("UNDER_REVIEW status does not allow a resolution action");
        }

        if (nextStatus == ReportStatus.DISMISSED && action != ReportResolutionAction.NO_ACTION) {
            throw new BadRequestException("DISMISSED status must use NO_ACTION");
        }

        if (nextStatus == ReportStatus.RESOLVED) {
            applyResolutionAction(report, action, request.getModeratorNote(), moderatorRole);
        }

        report.setStatus(nextStatus);
        report.setResolutionAction(action);
        report.setModeratorNote(trimToNull(request.getModeratorNote()));
        report.setReviewedBy(moderatorId);
        report.setReviewedAt(LocalDateTime.now());

        return mapToDTO(reportRepository.save(report));
    }

    private Report getReportEntity(Long reportId) {
        return reportRepository.findById(reportId)
                .orElseThrow(() -> new ResourceNotFoundException("Report not found: " + reportId));
    }

    private void applyResolutionAction(Report report,
                                       ReportResolutionAction action,
                                       String moderatorNote,
                                       String moderatorRole) {
        switch (action) {
            case NO_ACTION -> {
                return;
            }
            case REMOVE_POST -> {
                if (report.getTargetType() != ReportTargetType.POST) {
                    throw new BadRequestException("REMOVE_POST action requires a POST report target");
                }
                postService.adminForceDeletePost(
                        report.getTargetId(),
                        defaultNote(moderatorNote, "Removed after user reports")
                );
            }
            case REMOVE_COMMENT -> {
                if (report.getTargetType() != ReportTargetType.COMMENT) {
                    throw new BadRequestException("REMOVE_COMMENT action requires a COMMENT report target");
                }
                removeCommentWithModerationPrivileges(
                        report.getTargetId(),
                        defaultNote(moderatorNote, "Removed after user reports"),
                        moderatorRole
                );
            }
            case SUSPEND_ACCOUNT -> {
                if (report.getTargetType() != ReportTargetType.ACCOUNT) {
                    throw new BadRequestException("SUSPEND_ACCOUNT action requires an ACCOUNT report target");
                }
                requireAdminRole(moderatorRole);
                suspendAccount(report.getTargetId());
            }
            case DELETE_ACCOUNT -> {
                if (report.getTargetType() != ReportTargetType.ACCOUNT) {
                    throw new BadRequestException("DELETE_ACCOUNT action requires an ACCOUNT report target");
                }
                requireAdminRole(moderatorRole);
                deleteAccount(report.getTargetId());
            }
        }
    }

    private void requireAdminRole(String moderatorRole) {
        if (!"ROLE_ADMIN".equals(moderatorRole)) {
            throw new UnauthorizedAccessException("Only admins can execute account-level actions");
        }
    }

    private void suspendAccount(Long userId) {
        String url = authServiceUrl + "/auth/admin/users/" + userId + "/suspend";
        restTemplate.exchange(url, HttpMethod.PATCH, HttpEntity.EMPTY, Object.class);
    }

    private void deleteAccount(Long userId) {
        String url = authServiceUrl + "/auth/admin/users/" + userId;
        restTemplate.exchange(url, HttpMethod.DELETE, HttpEntity.EMPTY, Object.class);
    }

    private void removeCommentWithModerationPrivileges(Long commentId, String reason, String moderatorRole) {
        String url = UriComponentsBuilder
                .fromHttpUrl(commentServiceUrl + "/admin/comments/{commentId}")
                .queryParam("reason", reason)
                .buildAndExpand(commentId)
                .toUriString();

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-User-Role", moderatorRole == null ? "ROLE_USER" : moderatorRole);
        HttpEntity<Void> requestEntity = new HttpEntity<>(headers);

        restTemplate.exchange(url, HttpMethod.DELETE, requestEntity, Object.class);
    }

    private void validateTargetExists(ReportTargetType targetType, Long targetId) {
        switch (targetType) {
            case POST -> postRepository.findById(targetId)
                    .filter(post -> !Boolean.TRUE.equals(post.getIsDeleted()))
                    .orElseThrow(() -> new ResourceNotFoundException("Target post not found: " + targetId));
            case COMMENT -> {
                String url = commentServiceUrl + "/comments/" + targetId;
                try {
                    restTemplate.exchange(url, HttpMethod.GET, HttpEntity.EMPTY, Object.class);
                } catch (Exception ex) {
                    throw new ResourceNotFoundException("Target comment not found: " + targetId);
                }
            }
            case ACCOUNT -> {
                String url = authServiceUrl + "/auth/users/" + targetId;
                try {
                    restTemplate.exchange(url, HttpMethod.GET, HttpEntity.EMPTY, Object.class);
                } catch (Exception ex) {
                    throw new ResourceNotFoundException("Target account not found: " + targetId);
                }
            }
        }
    }

    private String defaultNote(String candidate, String fallback) {
        String trimmed = trimToNull(candidate);
        return trimmed == null ? fallback : trimmed;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private ReportResponseDTO mapToDTO(Report report) {
        return ReportResponseDTO.builder()
                .reportId(report.getReportId())
                .reporterId(report.getReporterId())
                .targetType(report.getTargetType())
                .targetId(report.getTargetId())
                .reason(report.getReason())
                .details(report.getDetails())
                .status(report.getStatus())
                .reviewedBy(report.getReviewedBy())
                .reviewedAt(report.getReviewedAt())
                .resolutionAction(report.getResolutionAction())
                .moderatorNote(report.getModeratorNote())
                .createdAt(report.getCreatedAt())
                .updatedAt(report.getUpdatedAt())
                .build();
    }
}
