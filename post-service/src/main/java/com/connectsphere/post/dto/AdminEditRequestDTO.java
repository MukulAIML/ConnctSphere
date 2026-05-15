package com.connectsphere.post.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Payload for admin force-edit endpoint.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request body for admin force-editing a post's content")
public class AdminEditRequestDTO {

    @Schema(description = "Replacement content for the post", example = "[Content removed by moderator]")
    private String content;

    @Schema(description = "Internal admin note explaining the action", example = "Removed hate-speech per policy §3.2")
    private String adminNote;
}
