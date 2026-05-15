package com.connectsphere.commentservice.dto;

import lombok.Data;

@Data
public class PostResponseDTO {
    private Long postId;
    private Long authorId;
    private String content;
}
