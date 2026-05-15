package com.connectsphere.post.dto;

import com.connectsphere.post.entity.PostType;
import com.connectsphere.post.entity.Visibility;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class PostRequestDTO {

    private String content;

    private List<String> mediaUrls;

    @NotNull(message = "Post type is required")
    private PostType postType;

    @NotNull(message = "Visibility is required")
    private Visibility visibility;
}
