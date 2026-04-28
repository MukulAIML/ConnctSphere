package com.connectsphere.search.dto;

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
public class IndexRequestDTO {

    @NotNull(message = "Post ID is required")
    private Long postId;

    @NotBlank(message = "Content is required to index hashtags")
    private String content;
}
