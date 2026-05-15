package com.connectsphere.post.dto;

import com.connectsphere.post.entity.ReportResolutionAction;
import com.connectsphere.post.entity.ReportStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ResolveReportRequestDTO {

    @NotNull(message = "Status is required")
    private ReportStatus status;

    @Builder.Default
    private ReportResolutionAction resolutionAction = ReportResolutionAction.NO_ACTION;

    @Size(max = 2000, message = "Moderator note must be at most 2000 characters")
    private String moderatorNote;
}
