package com.connectsphere.commentservice.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "Request body for updating the text of an existing comment.")
public class UpdateCommentRequest {

    @NotBlank
    @Size(min = 1, max = 2000)
    @Schema(description = "New comment text. 1–2000 characters.",
            example = "Updated my thoughts — really insightful article!", required = true)
    private String content;
}
