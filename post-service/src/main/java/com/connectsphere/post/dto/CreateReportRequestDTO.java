package com.connectsphere.post.dto;

import com.connectsphere.post.entity.ReportReason;
import com.connectsphere.post.entity.ReportTargetType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateReportRequestDTO {

    @NotNull(message = "Target type is required")
    private ReportTargetType targetType;

    @NotNull(message = "Target id is required")
    @Positive(message = "Target id must be greater than 0")
    private Long targetId;

    @NotNull(message = "Reason is required")
    private ReportReason reason;

    @Size(max = 2000, message = "Details must be at most 2000 characters")
    private String details;
}
