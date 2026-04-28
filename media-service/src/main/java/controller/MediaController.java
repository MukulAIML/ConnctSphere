package com.connectsphere.media.controller;

import com.connectsphere.media.dto.ApiResponse;
import com.connectsphere.media.dto.MediaRequestDTO;
import com.connectsphere.media.dto.MediaResponseDTO;
import com.connectsphere.media.dto.MediaUrlsUpdateDTO;
import com.connectsphere.media.service.MediaService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/media")
public class MediaController {

    private static final Logger logger = LoggerFactory.getLogger(MediaController.class);

    private final MediaService mediaService;

    public MediaController(MediaService mediaService) {
        this.mediaService = mediaService;
    }

    private Long getAuthenticatedUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() != null) {
            try {
                return Long.valueOf(authentication.getPrincipal().toString());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<MediaResponseDTO>> uploadMedia(@Valid @RequestBody MediaRequestDTO requestDTO) {
        Long uploaderId = getAuthenticatedUserId();
        if (uploaderId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error(HttpStatus.UNAUTHORIZED.value(), "User not authenticated"));
        }
        logger.info("Received request to upload media by user: {}", uploaderId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created("Media uploaded successfully", mediaService.uploadMedia(requestDTO, uploaderId)));
    }

    @PostMapping("/upload")
    public ResponseEntity<ApiResponse<MediaResponseDTO>> uploadFile(@RequestParam("file") MultipartFile file) {
        Long uploaderId = getAuthenticatedUserId();
        if (uploaderId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error(HttpStatus.UNAUTHORIZED.value(), "User not authenticated"));
        }
        logger.info("Received request to upload file {} by user: {}", file.getOriginalFilename(), uploaderId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created("File uploaded successfully", mediaService.saveFile(file, uploaderId)));
    }

    @GetMapping("/post/{postId}")
    public ResponseEntity<ApiResponse<List<MediaResponseDTO>>> getMediaByPostId(@PathVariable("postId") Long postId) {
        logger.info("Received request to fetch media for post: {}", postId);
        return ResponseEntity.ok(ApiResponse.success("Media fetched successfully", mediaService.getMediaByPostId(postId)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<MediaResponseDTO>> getMediaById(@PathVariable("id") Long id) {
        logger.info("Received request to fetch media: {}", id);
        return ResponseEntity.ok(ApiResponse.success("Media fetched successfully", mediaService.getMediaById(id)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteMedia(@PathVariable("id") Long id) {
        Long uploaderId = getAuthenticatedUserId();
        if (uploaderId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error(HttpStatus.UNAUTHORIZED.value(), "User not authenticated"));
        }
        logger.info("Received request to delete media {} by user: {}", id, uploaderId);
        mediaService.deleteMedia(id, uploaderId);
        return ResponseEntity.ok(ApiResponse.success("Media deleted successfully", null));
    }

    @PutMapping("/soft-delete")
    public ResponseEntity<ApiResponse<Integer>> softDeleteByUrls(@Valid @RequestBody MediaUrlsUpdateDTO requestDTO) {
        logger.info("Received request to soft delete media URLs, count={}", requestDTO.getMediaUrls() == null ? 0 : requestDTO.getMediaUrls().size());
        int updated = mediaService.softDeleteMediaByUrls(requestDTO);
        return ResponseEntity.ok(ApiResponse.success("Media soft-deleted successfully", updated));
    }
}
