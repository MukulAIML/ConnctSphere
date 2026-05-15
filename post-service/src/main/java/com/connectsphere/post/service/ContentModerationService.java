package com.connectsphere.post.service;

import com.connectsphere.post.entity.Post;
import com.connectsphere.post.repository.PostRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.rekognition.RekognitionClient;
import software.amazon.awssdk.services.rekognition.model.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Optional;

/**
 * Content Moderation Service.
 *
 * <p>Scans post media (images) using AWS Rekognition's DetectModerationLabels API.
 * Text content is checked via a simple keyword-based heuristic when Rekognition
 * is disabled or when there are no media URLs.
 *
 * <p>Designed to complete within the 30-second SLA from post creation.
 * The scan is executed asynchronously so it does not block the HTTP response.
 */
@Service
public class ContentModerationService {

    private static final Logger logger = LoggerFactory.getLogger(ContentModerationService.class);

    private final RekognitionClient rekognitionClient;
    private final PostRepository postRepository;

    @Value("${aws.rekognition.enabled:false}")
    private boolean rekognitionEnabled;

    @Value("${aws.rekognition.moderation-threshold:75}")
    private float moderationThreshold;

    // Minimal text-based toxic keyword list (extend as needed)
    private static final List<String> TOXIC_KEYWORDS = List.of(
            "hate", "violence", "abuse", "explicit", "nsfw", "gore", "kill", "murder"
    );

    public ContentModerationService(RekognitionClient rekognitionClient,
                                    PostRepository postRepository) {
        this.rekognitionClient = rekognitionClient;
        this.postRepository = postRepository;
    }

    /**
     * Asynchronously scans a newly created post for inappropriate content.
     * Runs in a separate thread so the caller's HTTP response is not delayed.
     * Must complete within 30 seconds of invocation per the SLA requirement.
     *
     * @param postId ID of the post to moderate
     */
    @Async
    public void moderatePostAsync(Long postId) {
        try {
            logger.info("[Moderation] Starting async scan for postId={}", postId);

            Optional<Post> optPost = postRepository.findById(postId);
            if (optPost.isEmpty()) {
                logger.warn("[Moderation] Post {} not found — skipping", postId);
                return;
            }
            Post post = optPost.get();

            ModerationResult result = scanPost(post);

            if (result.isFlagged()) {
                post.setIsFlagged(true);
                post.setModerationLabel(result.label());
                post.setModerationScore(result.score());
                postRepository.save(post);
                logger.warn("[Moderation] Post {} FLAGGED — label='{}', score={}",
                        postId, result.label(), result.score());
            } else {
                logger.info("[Moderation] Post {} CLEAN", postId);
            }

        } catch (Exception e) {
            logger.error("[Moderation] Error scanning postId={}: {}", postId, e.getMessage(), e);
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Internal helpers
    // ──────────────────────────────────────────────────────────────────────────

    private ModerationResult scanPost(Post post) {
        // 1) Try image scan via Rekognition if enabled and media URLs present
        if (rekognitionEnabled && post.getMediaUrls() != null && !post.getMediaUrls().isEmpty()) {
            for (String url : post.getMediaUrls()) {
                ModerationResult result = scanImageUrl(url);
                if (result.isFlagged()) return result;
            }
        }

        // 2) Fallback: text keyword scan
        if (post.getContent() != null && !post.getContent().isBlank()) {
            return scanTextKeywords(post.getContent());
        }

        return ModerationResult.clean();
    }

    /**
     * Downloads the image at {@code imageUrl} and passes raw bytes to Rekognition.
     */
    private ModerationResult scanImageUrl(String imageUrl) {
        try {
            // Download image bytes
            HttpClient httpClient = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(imageUrl))
                    .GET()
                    .build();
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());

            if (response.statusCode() != 200) {
                logger.warn("[Moderation] Could not download image {}: HTTP {}", imageUrl, response.statusCode());
                return ModerationResult.clean();
            }

            SdkBytes imageBytes = SdkBytes.fromByteArray(response.body());

            DetectModerationLabelsRequest rekRequest = DetectModerationLabelsRequest.builder()
                    .image(Image.builder()
                            .bytes(imageBytes)
                            .build())
                    .minConfidence(moderationThreshold)
                    .build();

            DetectModerationLabelsResponse rekResponse = rekognitionClient.detectModerationLabels(rekRequest);

            if (rekResponse.moderationLabels() != null && !rekResponse.moderationLabels().isEmpty()) {
                ModerationLabel top = rekResponse.moderationLabels().get(0);
                logger.warn("[Moderation] Rekognition flagged image {}: label='{}', confidence={}",
                        imageUrl, top.name(), top.confidence());
                return new ModerationResult(true, top.name(), top.confidence());
            }

        } catch (Exception e) {
            logger.error("[Moderation] Rekognition call failed for {}: {}", imageUrl, e.getMessage());
        }
        return ModerationResult.clean();
    }

    /**
     * Lightweight text keyword scan used when Rekognition is disabled or content is text-only.
     */
    private ModerationResult scanTextKeywords(String content) {
        String lower = content.toLowerCase();
        for (String keyword : TOXIC_KEYWORDS) {
            if (lower.contains(keyword)) {
                logger.warn("[Moderation] Text keyword '{}' detected", keyword);
                return new ModerationResult(true, "Toxic-Text:" + keyword, 100f);
            }
        }
        return ModerationResult.clean();
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Value object for moderation outcomes
    // ──────────────────────────────────────────────────────────────────────────

    public record ModerationResult(boolean isFlagged, String label, float score) {
        public static ModerationResult clean() {
            return new ModerationResult(false, null, 0f);
        }
    }
}
