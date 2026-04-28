package com.connectsphere.media.serviceImpl;

import com.connectsphere.media.dto.MediaRequestDTO;
import com.connectsphere.media.dto.MediaResponseDTO;
import com.connectsphere.media.dto.MediaUrlsUpdateDTO;
import com.connectsphere.media.entity.MediaEntity;
import com.connectsphere.media.exception.BadRequestException;
import com.connectsphere.media.exception.ResourceNotFoundException;
import com.connectsphere.media.exception.UnauthorizedAccessException;
import com.connectsphere.media.repository.MediaRepository;
import com.connectsphere.media.service.MediaService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;
import java.util.Collections;
import java.util.Set;
import org.springframework.web.client.RestTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;
import com.connectsphere.media.entity.MediaType;

@Service
public class MediaServiceImpl implements MediaService {

    private static final Logger logger = LoggerFactory.getLogger(MediaServiceImpl.class);

    private final MediaRepository mediaRepository;
    private final RestTemplate restTemplate;

    @Value("${post-service.url}")
    private String postServiceUrl;

    @Value("${media.upload.max-image-kb:10240}")
    private long maxImageKb;

    @Value("${media.upload.max-video-kb:51200}")
    private long maxVideoKb;

    private static final Set<String> ALLOWED_IMAGE_TYPES = Set.of("image/jpeg", "image/png", "image/webp");
    private static final String ALLOWED_VIDEO_TYPE = "video/mp4";

    public MediaServiceImpl(MediaRepository mediaRepository, RestTemplate restTemplate) {
        this.mediaRepository = mediaRepository;
        this.restTemplate = restTemplate;
    }

    @Override
    public MediaResponseDTO uploadMedia(MediaRequestDTO requestDTO, Long uploaderId) {
        MediaEntity media = MediaEntity.builder()
                .uploaderId(uploaderId)
                .url(requestDTO.getUrl())
                .mediaType(requestDTO.getMediaType())
                .sizeKb(requestDTO.getSizeKb())
                .mimeType(requestDTO.getMimeType())
                .linkedPostId(requestDTO.getLinkedPostId())
                .isDeleted(false)
                .build();

        MediaEntity savedMedia = mediaRepository.save(media);
        
        // Notify Post Service to update mediaUrls
        if (savedMedia.getLinkedPostId() != null) {
            updatePostServiceMedia(savedMedia.getLinkedPostId(), savedMedia.getUrl());
        }
        
        return mapToDTO(savedMedia);
    }

    @Override
    public MediaResponseDTO saveFile(MultipartFile file, Long uploaderId) {
        try {
            validateUpload(file);

            String uploadDir = "uploads/";
            Path uploadPath = Paths.get(uploadDir);
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }

            String fileName = UUID.randomUUID().toString() + "_" + file.getOriginalFilename();
            Path filePath = uploadPath.resolve(fileName);
            Files.copy(file.getInputStream(), filePath);

            // In local dev, we return the path relative to the service. 
            // The gateway will proxy this.
            String fileUrl = "/api/v1/media/files/" + fileName;
            String contentType = file.getContentType();
            long sizeKb = Math.max(1L, file.getSize() / 1024);

            MediaEntity media = MediaEntity.builder()
                    .uploaderId(uploaderId)
                    .url(fileUrl)
                    .mediaType(contentType != null && contentType.startsWith("video") ? MediaType.VIDEO : MediaType.IMAGE)
                    .sizeKb(sizeKb)
                    .mimeType(contentType)
                    .isDeleted(false)
                    .build();

            return mapToDTO(mediaRepository.save(media));
        } catch (IOException e) {
            logger.error("Failed to save file: {}", e.getMessage());
            throw new RuntimeException("Could not store file. Error: " + e.getMessage());
        }
    }

    private void updatePostServiceMedia(Long postId, String url) {
        try {
            String updateUrl = postServiceUrl + "/posts/" + postId + "/mediaUrls";
            MediaUrlsUpdateDTO updateDTO = new MediaUrlsUpdateDTO(Collections.singletonList(url));
            restTemplate.exchange(updateUrl, HttpMethod.PUT, new HttpEntity<>(updateDTO), Void.class);
            logger.info("Successfully requested mediaUrls update for postId: {}", postId);
        } catch (Exception e) {
            logger.error("Failed to update mediaUrls in post-service for postId: {}: {}", postId, e.getMessage());
        }
    }

    @Override
    public List<MediaResponseDTO> getMediaByPostId(Long postId) {
        List<MediaEntity> medias = mediaRepository.findByLinkedPostIdAndIsDeletedFalse(postId);
        return medias.stream().map(this::mapToDTO).collect(Collectors.toList());
    }

    @Override
    public MediaResponseDTO getMediaById(Long mediaId) {
        MediaEntity media = mediaRepository.findById(mediaId)
                .filter(m -> !m.getIsDeleted())
                .orElseThrow(() -> new ResourceNotFoundException("Media not found or deleted"));
        return mapToDTO(media);
    }

    @Override
    public void deleteMedia(Long mediaId, Long uploaderId) {
        MediaEntity media = mediaRepository.findById(mediaId)
                .orElseThrow(() -> new ResourceNotFoundException("Media not found"));

        if (!media.getUploaderId().equals(uploaderId)) {
            throw new UnauthorizedAccessException("You can only delete your own media");
        }

        media.setIsDeleted(true);
        mediaRepository.save(media);
    }

    @Override
    public int softDeleteMediaByUrls(MediaUrlsUpdateDTO requestDTO) {
        if (requestDTO == null || requestDTO.getMediaUrls() == null || requestDTO.getMediaUrls().isEmpty()) {
            return 0;
        }

        List<MediaEntity> mediaList = mediaRepository.findByUrlInAndIsDeletedFalse(requestDTO.getMediaUrls());
        mediaList.forEach(media -> media.setIsDeleted(true));
        mediaRepository.saveAll(mediaList);
        return mediaList.size();
    }

    private void validateUpload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Uploaded file must not be empty");
        }

        String contentType = file.getContentType();
        if (contentType == null) {
            throw new BadRequestException("File MIME type is required");
        }

        long sizeKb = Math.max(1L, file.getSize() / 1024);
        if (contentType.startsWith("image/")) {
            if (!ALLOWED_IMAGE_TYPES.contains(contentType.toLowerCase())) {
                throw new BadRequestException("Only JPEG, PNG, and WebP images are supported");
            }
            if (sizeKb > maxImageKb) {
                throw new BadRequestException("Image exceeds the allowed size limit");
            }
        } else if (contentType.startsWith("video/")) {
            if (!ALLOWED_VIDEO_TYPE.equalsIgnoreCase(contentType)) {
                throw new BadRequestException("Only MP4 videos are supported");
            }
            if (sizeKb > maxVideoKb) {
                throw new BadRequestException("Video exceeds the allowed size limit");
            }
        } else {
            throw new BadRequestException("Unsupported media type");
        }
    }

    private MediaResponseDTO mapToDTO(MediaEntity media) {
        return MediaResponseDTO.builder()
                .mediaId(media.getMediaId())
                .uploaderId(media.getUploaderId())
                .url(media.getUrl())
                .mediaType(media.getMediaType())
                .sizeKb(media.getSizeKb())
                .mimeType(media.getMimeType())
                .linkedPostId(media.getLinkedPostId())
                .uploadedAt(media.getUploadedAt())
                .build();
    }
}
