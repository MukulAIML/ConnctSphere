package com.connectsphere.media.dto;

import com.connectsphere.media.entity.MediaType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class StoryResponseDTO {

    private Long storyId;
    private Long authorId;
    private String mediaUrl;
    private String caption;
    private MediaType mediaType;
    private Integer viewsCount;
    private LocalDateTime createdAt;
    private LocalDateTime expiresAt;
    private Boolean isActive;
}
