package com.connectsphere.media.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

/**
 * Service responsible for all AWS S3 upload / delete operations.
 * Returned URLs are CloudFront CDN URLs when a domain is configured,
 * otherwise they fall back to the S3 path-style URL.
 */
@Service
public class S3StorageService {

    private static final Logger logger = LoggerFactory.getLogger(S3StorageService.class);
    private static final String LOCAL_MEDIA_PREFIX = "/api/v1/media/files/";

    private final S3Client s3Client;

    @Value("${aws.s3.bucket-name}")
    private String bucketName;

    @Value("${aws.s3.region}")
    private String region;

    @Value("${aws.cloudfront.domain:}")
    private String cloudfrontDomain;

    @Value("${media.local.storage-path:/tmp/connectsphere-media}")
    private String localStoragePath;

    public S3StorageService(S3Client s3Client) {
        this.s3Client = s3Client;
    }

    /**
     * Upload a multipart file to S3 and return its public CDN/S3 URL.
     *
     * @param file      the file to upload
     * @param folder    logical folder prefix inside the bucket (e.g. "images", "videos")
     * @return          CloudFront URL if domain is set, otherwise S3 HTTPS URL
     */
    public String uploadFile(MultipartFile file, String folder) {
        String originalName = file.getOriginalFilename() != null
                ? file.getOriginalFilename().replaceAll("[^a-zA-Z0-9._-]", "_")
                : "file";
        String key = folder + "/" + UUID.randomUUID() + "_" + originalName;

        try {
            PutObjectRequest putRequest = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .contentType(file.getContentType())
                    .contentLength(file.getSize())
                    .build();

            s3Client.putObject(putRequest, RequestBody.fromInputStream(file.getInputStream(), file.getSize()));
            logger.info("Uploaded file to S3: bucket={}, key={}", bucketName, key);

            return buildPublicUrl(key);

        } catch (Exception e) {
            logger.warn("S3 upload failed for key={} ({}). Falling back to local storage.",
                    key, e.getMessage());
            return saveLocally(file, key);
        }
    }

    /**
     * Delete an object from S3 by its full public URL.
     * Silently skips if the URL does not belong to the configured bucket.
     */
    public void deleteByUrl(String publicUrl) {
        String localKey = extractLocalKeyFromUrl(publicUrl);
        if (localKey != null) {
            deleteLocalFile(localKey);
            return;
        }

        String key = extractKeyFromUrl(publicUrl);
        if (key == null) {
            logger.warn("Cannot derive S3 key from URL, skipping delete: {}", publicUrl);
            return;
        }
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build());
            logger.info("Deleted S3 object: bucket={}, key={}", bucketName, key);
        } catch (Exception e) {
            logger.error("Failed to delete S3 object key={}: {}", key, e.getMessage());
        }
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    /**
     * Build the public-facing URL for an S3 key.
     * Prefers CloudFront CDN when {@code aws.cloudfront.domain} is set.
     */
    private String buildPublicUrl(String key) {
        if (cloudfrontDomain != null && !cloudfrontDomain.isBlank()) {
            return "https://" + cloudfrontDomain.strip() + "/" + key;
        }
        // Fallback: virtual-hosted-style S3 URL
        return "https://" + bucketName + ".s3." + region + ".amazonaws.com/" + key;
    }

    /**
     * Attempt to reverse-derive the S3 object key from a CloudFront or S3 URL.
     * Returns {@code null} if the URL is unrecognised.
     */
    private String extractKeyFromUrl(String url) {
        if (url == null || url.isBlank()) return null;

        // CloudFront URL: https://<domain>/<key>
        if (cloudfrontDomain != null && !cloudfrontDomain.isBlank()
                && url.contains(cloudfrontDomain)) {
            int idx = url.indexOf(cloudfrontDomain) + cloudfrontDomain.length();
            String path = url.substring(idx);
            return path.startsWith("/") ? path.substring(1) : path;
        }

        // S3 virtual-hosted URL: https://<bucket>.s3.<region>.amazonaws.com/<key>
        String s3Host = bucketName + ".s3." + region + ".amazonaws.com/";
        int idx = url.indexOf(s3Host);
        if (idx >= 0) {
            return url.substring(idx + s3Host.length());
        }

        return null;
    }

    private String saveLocally(MultipartFile file, String key) {
        try {
            Path baseDir = Paths.get(localStoragePath).toAbsolutePath().normalize();
            Path target = baseDir.resolve(key).normalize();
            if (!target.startsWith(baseDir)) {
                throw new IllegalStateException("Invalid local media path resolution");
            }

            Files.createDirectories(target.getParent());
            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
            }
            logger.info("Stored media locally at path={}", target);
            return LOCAL_MEDIA_PREFIX + key;
        } catch (IOException ioException) {
            logger.error("Failed to store media locally for key={}: {}", key, ioException.getMessage());
            throw new RuntimeException("Could not store uploaded file", ioException);
        }
    }

    private String extractLocalKeyFromUrl(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }

        String normalized = url.trim();
        String key = null;
        if (normalized.startsWith(LOCAL_MEDIA_PREFIX)) {
            key = normalized.substring(LOCAL_MEDIA_PREFIX.length());
        } else if (normalized.startsWith("/media/files/")) {
            key = normalized.substring("/media/files/".length());
        }

        if (key == null || key.isBlank()) {
            return null;
        }

        Path cleaned = Paths.get(key).normalize();
        String cleanedKey = cleaned.toString().replace('\\', '/');
        if (cleanedKey.startsWith("..")) {
            return null;
        }
        return cleanedKey;
    }

    private void deleteLocalFile(String key) {
        try {
            Path baseDir = Paths.get(localStoragePath).toAbsolutePath().normalize();
            Path target = baseDir.resolve(key).normalize();
            if (!target.startsWith(baseDir)) {
                logger.warn("Rejected local delete outside media root: {}", target);
                return;
            }

            Files.deleteIfExists(target);
            logger.info("Deleted local media file: {}", target);
        } catch (Exception ex) {
            logger.error("Failed to delete local media key={}: {}", key, ex.getMessage());
        }
    }
}
