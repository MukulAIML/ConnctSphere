package com.connectsphere.media.serviceImpl;

import com.connectsphere.media.dto.MediaRequestDTO;
import com.connectsphere.media.dto.MediaResponseDTO;
import com.connectsphere.media.dto.MediaUrlsUpdateDTO;
import com.connectsphere.media.entity.MediaEntity;
import com.connectsphere.media.entity.MediaType;
import com.connectsphere.media.exception.BadRequestException;
import com.connectsphere.media.exception.ResourceNotFoundException;
import com.connectsphere.media.exception.UnauthorizedAccessException;
import com.connectsphere.media.repository.MediaRepository;
import com.connectsphere.media.service.MediaService;
import com.connectsphere.media.storage.S3StorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class MediaServiceImpl implements MediaService {

    private static final Logger logger = LoggerFactory.getLogger(MediaServiceImpl.class);

    private final MediaRepository mediaRepository;
    private final RestTemplate restTemplate;
    private final S3StorageService s3StorageService;

    @Value("${post-service.url}")
    private String postServiceUrl;

    @Value("${media.upload.max-image-kb:10240}")
    private long maxImageKb;

    @Value("${media.upload.max-video-kb:51200}")
    private long maxVideoKb;

    private static final Set<String> ALLOWED_IMAGE_TYPES = Set.of("image/jpeg", "image/png", "image/webp");
    private static final Set<String> ALLOWED_VIDEO_TYPES = Set.of(
            "video/mp4",
            "video/webm",
            "video/quicktime",
            "video/x-m4v",
            "video/m4v"
    );

    public MediaServiceImpl(MediaRepository mediaRepository,
                            RestTemplate restTemplate,
                            S3StorageService s3StorageService) {
        this.mediaRepository = mediaRepository;
        this.restTemplate = restTemplate;
        this.s3StorageService = s3StorageService;
    }

    @Override
    public MediaResponseDTO uploadMedia(MediaRequestDTO requestDTO, Long uploaderId) {
        validateMediaDetails(requestDTO.getMediaType(), requestDTO.getMimeType(), requestDTO.getSizeKb());

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

        if (savedMedia.getLinkedPostId() != null) {
            updatePostServiceMedia(savedMedia.getLinkedPostId(), savedMedia.getUrl());
        }

        return mapToDTO(savedMedia);
    }

    /**
     * Upload a raw file to AWS S3; the returned URL is the CloudFront CDN URL
     * (or S3 URL if CloudFront is not configured). No local disk I/O occurs.
     */
    @Override
    public MediaResponseDTO saveFile(MultipartFile file, Long uploaderId) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Uploaded file must not be empty");
        }

        String contentType = resolveUploadMimeType(file);
        long sizeKb = Math.max(1L, file.getSize() / 1024);
        MediaType mediaType = resolveMediaType(contentType);
        validateMediaDetails(mediaType, contentType, sizeKb);
        String folder = mediaType == MediaType.VIDEO ? "videos" : "images";

        // Upload to S3 → get CDN/S3 URL back
        String fileUrl = s3StorageService.uploadFile(file, folder);

        MediaEntity media = MediaEntity.builder()
                .uploaderId(uploaderId)
                .url(fileUrl)
                .mediaType(mediaType)
                .sizeKb(sizeKb)
                .mimeType(contentType)
                .isDeleted(false)
                .build();

        return mapToDTO(mediaRepository.save(media));
    }

    @Override
    public List<MediaResponseDTO> getMediaByPostId(Long postId) {
        return mediaRepository.findByLinkedPostIdAndIsDeletedFalse(postId)
                .stream().map(this::mapToDTO).collect(Collectors.toList());
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

        // Soft-delete in DB only to preserve audit trail.
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

    // ---------------------------------------------------------------
    // Private helpers
    // ---------------------------------------------------------------

    private void updatePostServiceMedia(Long postId, String url) {
        try {
            String updateUrl = postServiceUrl + "/posts/" + postId + "/mediaUrls";
            MediaUrlsUpdateDTO updateDTO = new MediaUrlsUpdateDTO(Collections.singletonList(url));
            restTemplate.exchange(updateUrl, HttpMethod.PUT, new HttpEntity<>(updateDTO), Void.class);
            logger.info("Successfully requested mediaUrls update for postId: {}", postId);
        } catch (Exception e) {
            logger.error("Failed to update mediaUrls in post-service for postId={}: {}", postId, e.getMessage());
        }
    }

    private void validateMediaDetails(MediaType mediaType, String mimeType, Long sizeKb) {
        if (mediaType == null) {
            throw new BadRequestException("Media type is required");
        }
        if (mimeType == null || mimeType.isBlank()) {
            throw new BadRequestException("File MIME type is required");
        }
        if (sizeKb == null || sizeKb <= 0) {
            throw new BadRequestException("File size must be greater than zero");
        }

        String normalizedMimeType = normalizeMimeType(mimeType);
        MediaType resolvedType = resolveMediaType(normalizedMimeType);
        if (mediaType != resolvedType) {
            throw new BadRequestException("mediaType does not match mimeType");
        }

        if (mediaType == MediaType.IMAGE) {
            if (sizeKb > maxImageKb) {
                throw new BadRequestException("Image exceeds the allowed size limit");
            }
            return;
        }

        if (sizeKb > maxVideoKb) {
            throw new BadRequestException("Video exceeds the allowed size limit");
        }
    }

    private String normalizeMimeType(String mimeType) {
        if (mimeType == null) {
            return null;
        }
        String normalized = mimeType.trim().toLowerCase(Locale.ROOT);
        int semicolonIndex = normalized.indexOf(';');
        if (semicolonIndex >= 0) {
            normalized = normalized.substring(0, semicolonIndex).trim();
        }
        return normalized;
    }

    private MediaType resolveMediaType(String normalizedMimeType) {
        if (normalizedMimeType == null || normalizedMimeType.isBlank()) {
            throw new BadRequestException("File MIME type is required");
        }

        if (ALLOWED_IMAGE_TYPES.contains(normalizedMimeType)) {
            return MediaType.IMAGE;
        }
        if (ALLOWED_VIDEO_TYPES.contains(normalizedMimeType)) {
            return MediaType.VIDEO;
        }
        throw new BadRequestException("Only JPEG, PNG, WebP images and MP4/WebM/MOV/M4V videos are supported");
    }

    private String resolveUploadMimeType(MultipartFile file) {
        String normalized = normalizeMimeType(file.getContentType());
        if (normalized != null && !normalized.isBlank() && !"application/octet-stream".equals(normalized)) {
            return normalized;
        }

        String inferred = inferMimeTypeFromFilename(file.getOriginalFilename());
        return inferred != null ? inferred : normalized;
    }

    private String inferMimeTypeFromFilename(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            return null;
        }

        String name = originalFilename.trim().toLowerCase(Locale.ROOT);
        if (name.endsWith(".jpg") || name.endsWith(".jpeg")) return "image/jpeg";
        if (name.endsWith(".png")) return "image/png";
        if (name.endsWith(".webp")) return "image/webp";
        if (name.endsWith(".mp4")) return "video/mp4";
        if (name.endsWith(".webm")) return "video/webm";
        if (name.endsWith(".mov")) return "video/quicktime";
        if (name.endsWith(".m4v")) return "video/x-m4v";
        return null;
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
