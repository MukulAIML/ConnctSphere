package com.connectsphere.media.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class MediaUrlsUpdateDTO {
    private List<String> mediaUrls;
}
