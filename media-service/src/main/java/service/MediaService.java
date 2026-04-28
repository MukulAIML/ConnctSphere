package com.connectsphere.media.service;

import com.connectsphere.media.dto.MediaRequestDTO;
import com.connectsphere.media.dto.MediaResponseDTO;
import com.connectsphere.media.dto.MediaUrlsUpdateDTO;

import java.util.List;

public interface MediaService {
    MediaResponseDTO uploadMedia(MediaRequestDTO requestDTO, Long uploaderId);
    MediaResponseDTO saveFile(org.springframework.web.multipart.MultipartFile file, Long uploaderId);
    List<MediaResponseDTO> getMediaByPostId(Long postId);
    MediaResponseDTO getMediaById(Long mediaId);
    void deleteMedia(Long mediaId, Long uploaderId);
    int softDeleteMediaByUrls(MediaUrlsUpdateDTO requestDTO);
}
