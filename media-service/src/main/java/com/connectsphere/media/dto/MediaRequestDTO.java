package com.connectsphere.media.dto;

import com.connectsphere.media.entity.MediaType;
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
public class MediaRequestDTO {

    @NotBlank(message = "URL is required")
    private String url;

    @NotNull(message = "Media type is required")
    private MediaType mediaType;

    @NotNull(message = "Size is required")
    private Long sizeKb;

    @NotBlank(message = "MIME type is required")
    private String mimeType;

    @NotNull(message = "Linked post ID is required")
    private Long linkedPostId;
}
