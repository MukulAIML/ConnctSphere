package com.connectsphere.post.dto;

import com.connectsphere.post.entity.ReportReason;
import com.connectsphere.post.entity.ReportResolutionAction;
import com.connectsphere.post.entity.ReportStatus;
import com.connectsphere.post.entity.ReportTargetType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReportResponseDTO {

    private Long reportId;
    private Long reporterId;
    private ReportTargetType targetType;
    private Long targetId;
    private ReportReason reason;
    private String details;
    private ReportStatus status;
    private Long reviewedBy;
    private LocalDateTime reviewedAt;
    private ReportResolutionAction resolutionAction;
    private String moderatorNote;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
