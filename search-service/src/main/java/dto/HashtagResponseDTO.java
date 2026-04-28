package com.connectsphere.search.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class HashtagResponseDTO {

    private Long hashtagId;
    private String tag;
    private Integer postCount;
    private LocalDateTime lastUsedAt;
}
