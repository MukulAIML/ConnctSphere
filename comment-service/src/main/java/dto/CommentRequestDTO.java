package com.connectsphere.comment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class CommentRequestDTO {

    @NotNull(message = "Post ID is required")
    private Long postId;

    private Long parentCommentId;

    @NotBlank(message = "Content cannot be blank")
    private String content;
}
