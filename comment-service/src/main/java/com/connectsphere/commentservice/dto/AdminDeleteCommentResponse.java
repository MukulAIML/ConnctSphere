package com.connectsphere.commentservice.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Result of an administrative comment deletion.")
public class AdminDeleteCommentResponse {

    @Schema(description = "The comment ID that was deleted.", example = "42")
    private int commentId;

    @Schema(description = "Total number of records removed (comment + cascaded replies).", example = "3")
    private int totalDeleted;

    @Schema(description = "Number of replies cascaded-deleted together with the parent.", example = "2")
    private int repliesDeleted;

    @Schema(description = "The post to which the deleted comment belonged.", example = "7")
    private int postId;

    @Schema(description = "Original author of the deleted comment.", example = "101")
    private int originalAuthorId;

    @Schema(description = "ID of the admin who performed the deletion.", example = "1")
    private int deletedByAdminId;

    @Schema(description = "Optional moderation reason supplied by the admin.",
            example = "Violates community guidelines — hate speech")
    private String reason;

    @Schema(description = "Timestamp when the deletion was recorded.")
    private LocalDateTime deletedAt;
}
