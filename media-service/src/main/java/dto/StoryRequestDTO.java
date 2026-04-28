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
public class StoryRequestDTO {

    @NotBlank(message = "Media URL is required")
    private String mediaUrl;

    private String caption;

    @NotNull(message = "Media type is required")
    private MediaType mediaType;
}
