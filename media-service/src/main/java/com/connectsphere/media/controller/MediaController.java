package com.connectsphere.media.controller;

import com.connectsphere.media.dto.ApiResponse;
import com.connectsphere.media.dto.MediaRequestDTO;
import com.connectsphere.media.dto.MediaResponseDTO;
import com.connectsphere.media.dto.MediaUrlsUpdateDTO;
import com.connectsphere.media.service.MediaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Tag(name = "Media", description = "Upload, retrieve and delete images/videos. Files are stored on AWS S3 and served via CloudFront CDN.")
@RestController
@RequestMapping("/media")
@SecurityRequirement(name = "bearerAuth")
public class MediaController {

    private static final Logger logger = LoggerFactory.getLogger(MediaController.class);

    private final MediaService mediaService;

    public MediaController(MediaService mediaService) {
        this.mediaService = mediaService;
    }

    // ------------------------------------------------------------------
    // POST /media  — register an already-hosted media URL
    // ------------------------------------------------------------------

    @Operation(
            summary = "Register a media record by URL",
            description = "Stores a media metadata record that references an already-hosted URL. "
                    + "Useful when the client has uploaded directly to S3 via a pre-signed URL.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Media record created"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation error"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthenticated")
    })
    @PostMapping
    public ResponseEntity<ApiResponse<MediaResponseDTO>> uploadMedia(
            @Valid @RequestBody MediaRequestDTO requestDTO) {

        Long uploaderId = getAuthenticatedUserId();
        if (uploaderId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error(HttpStatus.UNAUTHORIZED.value(), "User not authenticated"));
        }
        logger.info("Received request to register media URL by user: {}", uploaderId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created("Media uploaded successfully",
                        mediaService.uploadMedia(requestDTO, uploaderId)));
    }

    // ------------------------------------------------------------------
    // POST /media/upload  — multipart file upload → S3 + CloudFront URL
    // ------------------------------------------------------------------

    @Operation(
            summary = "Upload a file to S3",
            description = "Accepts a multipart/form-data file (JPEG, PNG, WebP, MP4, WebM, MOV, or M4V), uploads it to "
                    + "AWS S3, and returns the CloudFront CDN URL. "
                    + "Maximum sizes: images 10 MB, videos 50 MB (configurable).")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "File uploaded; CDN URL returned"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Unsupported type or size exceeded"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthenticated")
    })
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<MediaResponseDTO>> uploadFile(
            @Parameter(description = "Image (JPEG/PNG/WebP) or video (MP4/WebM/MOV/M4V) file", required = true)
            @RequestParam("file") MultipartFile file) {

        Long uploaderId = getAuthenticatedUserId();
        if (uploaderId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error(HttpStatus.UNAUTHORIZED.value(), "User not authenticated"));
        }
        logger.info("Received request to upload file {} by user: {}", file.getOriginalFilename(), uploaderId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created("File uploaded successfully",
                        mediaService.saveFile(file, uploaderId)));
    }

    // ------------------------------------------------------------------
    // GET /media/post/{postId}
    // ------------------------------------------------------------------

    @Operation(summary = "Get all media for a post",
            description = "Returns every non-deleted media record linked to the given post.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "List of media records"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthenticated")
    })
    @GetMapping("/post/{postId}")
    public ResponseEntity<ApiResponse<List<MediaResponseDTO>>> getMediaByPostId(
            @Parameter(description = "ID of the post", required = true)
            @PathVariable("postId") Long postId) {

        logger.info("Received request to fetch media for post: {}", postId);
        return ResponseEntity.ok(
                ApiResponse.success("Media fetched successfully", mediaService.getMediaByPostId(postId)));
    }

    // ------------------------------------------------------------------
    // GET /media/{id}
    // ------------------------------------------------------------------

    @Operation(summary = "Get a single media record by ID")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Media record found"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Not found or deleted"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthenticated")
    })
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<MediaResponseDTO>> getMediaById(
            @Parameter(description = "Media record ID", required = true)
            @PathVariable("id") Long id) {

        logger.info("Received request to fetch media: {}", id);
        return ResponseEntity.ok(
                ApiResponse.success("Media fetched successfully", mediaService.getMediaById(id)));
    }

    // ------------------------------------------------------------------
    // DELETE /media/{id}
    // ------------------------------------------------------------------

    @Operation(summary = "Delete a media record",
            description = "Soft-deletes the DB record and removes the file from S3. "
                    + "Only the original uploader may delete.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Media deleted"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthenticated"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden – not your media"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Not found")
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteMedia(
            @Parameter(description = "Media record ID", required = true)
            @PathVariable("id") Long id) {

        Long uploaderId = getAuthenticatedUserId();
        if (uploaderId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error(HttpStatus.UNAUTHORIZED.value(), "User not authenticated"));
        }
        logger.info("Received request to delete media {} by user: {}", id, uploaderId);
        mediaService.deleteMedia(id, uploaderId);
        return ResponseEntity.ok(ApiResponse.success("Media deleted successfully", null));
    }

    // ------------------------------------------------------------------
    // PUT /media/soft-delete  — internal inter-service call
    // ------------------------------------------------------------------

    @Operation(
            summary = "Soft-delete media by URLs (internal)",
            description = "Internal endpoint used by other micro-services (e.g. post-service) to bulk "
                    + "soft-delete media records and purge the corresponding S3 objects when a post is removed. "
                    + "**Does not require a JWT token.**",
            security = {}   // override global bearerAuth — this endpoint is permitAll
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "Number of records soft-deleted",
                    content = @Content(schema = @Schema(implementation = Integer.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid request body")
    })
    @PutMapping("/soft-delete")
    public ResponseEntity<ApiResponse<Integer>> softDeleteByUrls(
            @Valid @RequestBody MediaUrlsUpdateDTO requestDTO) {

        logger.info("Received request to soft-delete media URLs, count={}",
                requestDTO.getMediaUrls() == null ? 0 : requestDTO.getMediaUrls().size());
        int updated = mediaService.softDeleteMediaByUrls(requestDTO);
        return ResponseEntity.ok(ApiResponse.success("Media soft-deleted successfully", updated));
    }

    // ------------------------------------------------------------------

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
}
