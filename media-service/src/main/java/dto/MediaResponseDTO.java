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
public class MediaResponseDTO {

    private Long mediaId;
    private Long uploaderId;
    private String url;
    private MediaType mediaType;
    private Long sizeKb;
    private String mimeType;
    private Long linkedPostId;
    private LocalDateTime uploadedAt;
}
