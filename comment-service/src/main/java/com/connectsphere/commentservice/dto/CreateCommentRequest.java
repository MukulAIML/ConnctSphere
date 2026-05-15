package com.connectsphere.commentservice.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "Request body for creating a new comment or reply.")
public class CreateCommentRequest {

    @JsonIgnore
    @Schema(hidden = true)
    private Integer authorId;

    @Schema(description = "ID of the post being commented on. Required for top-level comments.",
            example = "5")
    private Integer postId;

    @Schema(description = "ID of the parent comment when creating a reply. " +
            "Omit (or set to null) for a top-level comment.",
            example = "10", nullable = true)
    private Integer parentCommentId;

    @NotBlank
    @Size(min = 1, max = 2000)
    @Schema(description = "Comment text. 1–2000 characters.", example = "Great post!", required = true)
    private String content;
}
