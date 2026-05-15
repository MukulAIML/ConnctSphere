package com.connectsphere.post.serviceImpl;

import com.connectsphere.post.dto.IndexRequestDTO;
import com.connectsphere.post.dto.MediaUrlsUpdateDTO;
import com.connectsphere.post.dto.PostRequestDTO;
import com.connectsphere.post.dto.PostResponseDTO;
import com.connectsphere.post.entity.Post;
import com.connectsphere.post.entity.Visibility;
import com.connectsphere.post.exception.ResourceNotFoundException;
import com.connectsphere.post.exception.UnauthorizedAccessException;
import com.connectsphere.post.messaging.PostEventPublisher;
import com.connectsphere.post.repository.PostRepository;
import com.connectsphere.post.service.ContentModerationService;
import com.connectsphere.post.service.FeedCacheService;
import com.connectsphere.post.service.PostService;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class PostServiceImpl implements PostService {

    private static final Logger logger = LoggerFactory.getLogger(PostServiceImpl.class);
    private static final Pattern MENTION_PATTERN = Pattern.compile("(?<![A-Za-z0-9_])@([A-Za-z0-9_]{3,50})");
    private static final Pattern HASHTAG_PATTERN = Pattern.compile("(?<![A-Za-z0-9_])#([A-Za-z0-9_]{1,100})");
    private static final int MAX_MENTIONS_PER_POST = 20;

    private final PostRepository postRepository;
    private final RestTemplate restTemplate;
    private final FeedCacheService feedCacheService;
    private final ContentModerationService contentModerationService;
    private final PostEventPublisher postEventPublisher;

    @Value("${search-service.url}")
    private String searchServiceUrl;

    @Value("${follow-service.url}")
    private String followServiceUrl;

    @Value("${media-service.url}")
    private String mediaServiceUrl;

    @Value("${auth-service.url}")
    private String authServiceUrl;

    @Value("${app.search.reindex-hashtags-on-startup:true}")
    private boolean reindexHashtagsOnStartup;

    public PostServiceImpl(PostRepository postRepository,
                           RestTemplate restTemplate,
                           FeedCacheService feedCacheService,
                           ContentModerationService contentModerationService,
                           PostEventPublisher postEventPublisher) {
        this.postRepository = postRepository;
        this.restTemplate = restTemplate;
        this.feedCacheService = feedCacheService;
        this.contentModerationService = contentModerationService;
        this.postEventPublisher = postEventPublisher;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void backfillHashtagsForExistingPostsOnStartup() {
        if (!reindexHashtagsOnStartup) {
            logger.info("[HashtagBackfill] Startup re-index is disabled.");
            return;
        }

        List<Post> posts = postRepository.findByIsDeletedFalseOrderByCreatedAtDesc();
        if (posts.isEmpty()) {
            logger.info("[HashtagBackfill] No posts found for hashtag backfill.");
            return;
        }

        long candidateCount = posts.stream()
                .filter(post -> hasIndexableHashtag(post.getContent()))
                .count();

        if (candidateCount == 0) {
            logger.info("[HashtagBackfill] No hashtag-bearing posts found for backfill.");
            return;
        }

        long indexedCount = 0;
        for (Post post : posts) {
            if (!hasIndexableHashtag(post.getContent())) {
                continue;
            }
            triggerSearchIndex(post);
            indexedCount++;
        }

        logger.info("[HashtagBackfill] Completed startup hashtag backfill for {} posts (scanned {}).",
                indexedCount, posts.size());
    }

    // ================= CREATE =================
    @Override
    @Transactional
    public PostResponseDTO createPost(PostRequestDTO dto, Long authorId) {
        Post post = Post.builder()
                .authorId(authorId)
                .content(dto.getContent())
                .mediaUrls(dto.getMediaUrls())
                .postType(dto.getPostType())
                .visibility(dto.getVisibility())
                .likesCount(0)
                .commentsCount(0)
                .sharesCount(0)
                .isDeleted(false)
                .isFlagged(false)
                .moderationReviewed(false)
                .build();

        Post saved = postRepository.save(post);
        triggerSearchIndex(saved);

        contentModerationService.moderatePostAsync(saved.getPostId());
        evictVisibilityAffectedFeeds(authorId);
        triggerMentionNotifications(authorId, saved.getPostId(), null, saved.getContent());

        return mapToDTO(saved);
    }

    // ================= GET =================
    @Override
    @Transactional(readOnly = true)
    public PostResponseDTO getPostById(Long postId) {
        Post post = getPostEntityById(postId);
        validateVisibilityAccess(post, getCurrentViewerId());
        return mapToDTO(post);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PostResponseDTO> getPostsByUser(Long userId) {
        Long viewerId = getCurrentViewerId();
        List<Post> posts;

        if (viewerId != null && viewerId.equals(userId)) {
            posts = postRepository.findByAuthorIdAndIsDeletedFalseOrderByCreatedAtDesc(userId);
        } else if (viewerId != null && followsAuthor(viewerId, userId)) {
            posts = postRepository.findByAuthorIdAndIsDeletedFalseAndVisibilityInOrderByCreatedAtDesc(
                    userId,
                    List.of(Visibility.PUBLIC, Visibility.FOLLOWERS_ONLY)
            );
        } else {
            posts = postRepository.findByAuthorIdAndIsDeletedFalseAndVisibilityOrderByCreatedAtDesc(
                    userId,
                    Visibility.PUBLIC
            );
        }

        return posts
                .stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    // ================= UPDATE =================
    @Override
    @Transactional
    public PostResponseDTO updatePost(Long postId, PostRequestDTO dto, Long authorId) {
        Post post = getPostEntityById(postId);
        validateAuthor(post, authorId);
        String previousContent = post.getContent();

        post.setContent(dto.getContent());
        post.setMediaUrls(dto.getMediaUrls());
        post.setPostType(dto.getPostType());
        post.setVisibility(dto.getVisibility());

        Post updated = postRepository.save(post);
        triggerSearchIndex(updated);

        contentModerationService.moderatePostAsync(updated.getPostId());
        evictVisibilityAffectedFeeds(authorId);
        triggerMentionNotifications(authorId, updated.getPostId(), previousContent, updated.getContent());

        return mapToDTO(updated);
    }

    // ================= DELETE =================
    @Override
    @Transactional
    public void deletePost(Long postId, Long authorId) {
        Post post = getPostEntityById(postId);
        validateAuthor(post, authorId);

        post.setIsDeleted(true);
        postRepository.save(post);

        triggerSearchRemove(postId);
        triggerMediaSoftDelete(post.getMediaUrls());
        evictVisibilityAffectedFeeds(authorId);
    }

    // ================= SEARCH =================
    @Override
    @Transactional(readOnly = true)
    public List<PostResponseDTO> searchPosts(String keyword) {
        String normalizedKeyword = keyword == null ? "" : keyword.trim();
        Long viewerId = getCurrentViewerId();

        List<Post> posts;
        if (viewerId == null) {
            posts = postRepository.searchPublicPostsByKeyword(normalizedKeyword);
        } else {
            List<Long> followeeIds = fetchFollowingIds(viewerId);
            if (followeeIds.isEmpty()) {
                followeeIds = List.of(-1L);
            }
            posts = postRepository.searchVisiblePostsByKeyword(normalizedKeyword, viewerId, followeeIds);
        }

        return posts
                .stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    // ================= GENERATE FEED (by followee list) =================
    @Override
    @Transactional(readOnly = true)
    public List<PostResponseDTO> generateFeed(List<Long> followeeIds) {
        List<Long> sanitizedFolloweeIds = sanitizeUserIds(followeeIds);
        if (sanitizedFolloweeIds.isEmpty()) {
            return List.of();
        }

        return postRepository.findVisibleFeedByFolloweeIds(sanitizedFolloweeIds)
                .stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    // ================= CHANGE VISIBILITY =================
    @Override
    @Transactional
    public PostResponseDTO changeVisibility(Long postId, Visibility visibility, Long authorId) {
        Post post = getPostEntityById(postId);
        validateAuthor(post, authorId);
        post.setVisibility(visibility);
        Post saved = postRepository.save(post);
        evictVisibilityAffectedFeeds(authorId);
        return mapToDTO(saved);
    }

    // ================= ENGAGEMENT =================
    @Override
    @Transactional
    public PostResponseDTO incrementLike(Long postId) {
        int updated = postRepository.incrementLikesCount(postId);
        if (updated == 0) {
            throw new ResourceNotFoundException("Post not found: " + postId);
        }
        return mapToDTO(getPostEntityById(postId));
    }

    @Override
    @Transactional
    public PostResponseDTO decrementLike(Long postId) {
        int updated = postRepository.decrementLikesCount(postId);
        if (updated == 0) {
            throw new ResourceNotFoundException("Post not found: " + postId);
        }
        return mapToDTO(getPostEntityById(postId));
    }

    @Override
    @Transactional
    public PostResponseDTO incrementComment(Long postId) {
        int updated = postRepository.incrementCommentsCount(postId);
        if (updated == 0) {
            throw new ResourceNotFoundException("Post not found: " + postId);
        }
        return mapToDTO(getPostEntityById(postId));
    }

    @Override
    @Transactional
    public PostResponseDTO decrementComment(Long postId) {
        int updated = postRepository.decrementCommentsCount(postId);
        if (updated == 0) {
            throw new ResourceNotFoundException("Post not found: " + postId);
        }
        return mapToDTO(getPostEntityById(postId));
    }

    // ================= REDIS-CACHED FEED =================
    @Override
    @Transactional(readOnly = true)
    public List<PostResponseDTO> getFeedByUserId(Long userId) {
        List<PostResponseDTO> cached = feedCacheService.getCachedFeed(userId);
        if (cached != null) {
            logger.info("[Feed] Cache HIT for userId={}", userId);
            return cached;
        }

        logger.info("[Feed] Cache MISS - computing feed for userId={}", userId);

        List<Long> followeeIds = fetchFollowingIds(userId).stream()
                .filter(id -> !id.equals(userId))
                .collect(Collectors.toList());

        List<Post> posts;
        if (followeeIds.isEmpty()) {
            posts = postRepository.findPersonalizedFeedForUserWithoutFollowees(userId);
        } else {
            posts = postRepository.findPersonalizedFeedForUserWithFollowees(userId, followeeIds);
        }

        List<PostResponseDTO> feed = posts.stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());

        feedCacheService.cacheFeed(userId, feed);
        return feed;
    }

    // ================= ALL POSTS =================
    @Override
    @Transactional(readOnly = true)
    public List<PostResponseDTO> getAllPosts() {
        Long viewerId = getCurrentViewerId();
        List<Post> posts;

        if (viewerId == null) {
            posts = postRepository.findByIsDeletedFalseAndVisibilityOrderByCreatedAtDesc(Visibility.PUBLIC);
        } else {
            List<Long> followeeIds = fetchFollowingIds(viewerId);
            if (followeeIds.isEmpty()) {
                posts = postRepository.findVisiblePostsForViewerWithoutFollowees(viewerId);
            } else {
                posts = postRepository.findVisiblePostsForViewerWithFollowees(viewerId, followeeIds);
            }
        }

        return posts
                .stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    // ================= MEDIA =================
    @Override
    @Transactional
    public PostResponseDTO updateMediaUrls(Long postId, List<String> newMediaUrls) {
        Post post = getPostEntityById(postId);

        if (post.getMediaUrls() == null) {
            post.setMediaUrls(new ArrayList<>());
        }

        if (newMediaUrls != null && !newMediaUrls.isEmpty()) {
            post.getMediaUrls().addAll(newMediaUrls);
        }

        return mapToDTO(postRepository.save(post));
    }

    // ================= ADMIN MODERATION =================

    @Override
    @Transactional
    public PostResponseDTO adminForceEditPost(Long postId, String newContent, String adminNote) {
        Post post = postRepository.findById(postId)
                .filter(p -> !p.getIsDeleted())
                .orElseThrow(() -> new ResourceNotFoundException("Post not found: " + postId));

        post.setContent(newContent);
        post.setAdminNote(adminNote);
        post.setIsFlagged(false);           // Admin reviewed it
        post.setModerationReviewed(true);

        Post saved = postRepository.save(post);
        triggerSearchIndex(saved);
        evictVisibilityAffectedFeeds(post.getAuthorId());
        triggerMentionNotifications(post.getAuthorId(), post.getPostId(), null, newContent);

        logger.warn("[AdminModeration] Post {} force-edited by admin. Note: {}", postId, adminNote);
        return mapToDTO(saved);
    }

    @Override
    @Transactional
    public void adminForceDeletePost(Long postId, String adminNote) {
        Post post = postRepository.findById(postId)
                .filter(p -> !p.getIsDeleted())
                .orElseThrow(() -> new ResourceNotFoundException("Post not found: " + postId));

        post.setIsDeleted(true);
        post.setAdminNote(adminNote);
        post.setModerationReviewed(true);
        postRepository.save(post);

        triggerSearchRemove(postId);
        triggerMediaSoftDelete(post.getMediaUrls());
        evictVisibilityAffectedFeeds(post.getAuthorId());

        logger.warn("[AdminModeration] Post {} force-deleted by admin. Note: {}", postId, adminNote);
    }

    @Override
    @Transactional
    public PostResponseDTO adminMarkReviewed(Long postId, String adminNote) {
        Post post = postRepository.findById(postId)
                .filter(p -> !p.getIsDeleted())
                .orElseThrow(() -> new ResourceNotFoundException("Post not found: " + postId));

        post.setModerationReviewed(true);
        post.setIsFlagged(false);
        if (adminNote != null && !adminNote.isBlank()) {
            post.setAdminNote(adminNote);
        }
        Post saved = postRepository.save(post);
        logger.info("[AdminModeration] Post {} marked reviewed. Note: {}", postId, adminNote);
        return mapToDTO(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PostResponseDTO> getFlaggedPosts() {
        return postRepository.findByIsFlaggedTrueAndIsDeletedFalseOrderByCreatedAtDesc()
                .stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    // ================= SEARCH SYNC =================
    private void triggerSearchIndex(Post post) {
        String contentForIndexing = post.getContent() == null ? "" : post.getContent();
        try {
            postEventPublisher.publishHashtagIndex(post.getPostId(), contentForIndexing);
        } catch (Exception e) {
            logger.warn("Failed to publish hashtag index event for postId={}: {}", post.getPostId(), e.getMessage());
            // Fallback to synchronous indexing so content is still discoverable.
            try {
                String url = searchServiceUrl + "/search/index";
                restTemplate.exchange(
                        url,
                        HttpMethod.POST,
                        new HttpEntity<>(new IndexRequestDTO(post.getPostId(), contentForIndexing)),
                        Void.class
                );
            } catch (Exception fallbackError) {
                logger.warn("Search indexing fallback skipped for postId={}", post.getPostId());
            }
        }
    }

    private void triggerSearchRemove(Long postId) {
        try {
            String url = searchServiceUrl + "/search/remove/" + postId;
            restTemplate.exchange(url, HttpMethod.DELETE, null, Void.class);
        } catch (Exception e) {
            logger.warn("Search remove skipped for postId={}", postId);
        }
    }

    private boolean hasIndexableHashtag(String content) {
        return content != null && HASHTAG_PATTERN.matcher(content).find();
    }

    private void triggerMediaSoftDelete(List<String> mediaUrls) {
        if (mediaUrls == null || mediaUrls.isEmpty()) return;
        try {
            String url = mediaServiceUrl + "/media/soft-delete";
            restTemplate.exchange(
                    url,
                    HttpMethod.PUT,
                    new HttpEntity<>(new MediaUrlsUpdateDTO(mediaUrls)),
                    Void.class
            );
        } catch (Exception e) {
            logger.warn("Media soft-delete sync skipped");
        }
    }

    // ================= HELPERS =================
    private Post getPostEntityById(Long postId) {
        return postRepository.findById(postId)
                .filter(p -> !p.getIsDeleted())
                .orElseThrow(() -> new ResourceNotFoundException("Post not found: " + postId));
    }

    private Long getCurrentViewerId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getPrincipal() == null) {
            return null;
        }

        Object principal = auth.getPrincipal();
        if (principal instanceof Number number) {
            return number.longValue();
        }
        if (principal instanceof String text) {
            try {
                return Long.valueOf(text);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private void validateAuthor(Post post, Long authorId) {
        if (!post.getAuthorId().equals(authorId)) {
            throw new UnauthorizedAccessException("Not authorized");
        }
    }

    private void validateVisibilityAccess(Post post, Long viewerId) {
        if (post.getVisibility() == Visibility.PUBLIC) {
            return;
        }

        if (viewerId != null && viewerId.equals(post.getAuthorId())) {
            return;
        }

        if (post.getVisibility() == Visibility.FOLLOWERS_ONLY
                && viewerId != null
                && followsAuthor(viewerId, post.getAuthorId())) {
            return;
        }

        throw new UnauthorizedAccessException("Post is not visible to this user");
    }

    private boolean followsAuthor(Long viewerId, Long authorId) {
        if (viewerId == null || authorId == null || viewerId.equals(authorId)) {
            return false;
        }
        return fetchFollowingIds(viewerId).contains(authorId);
    }

    private List<Long> fetchFollowingIds(Long userId) {
        try {
            String url = followServiceUrl + "/follows/following/" + userId;
            ResponseEntity<Object> response = restTemplate.exchange(url, HttpMethod.GET, null, Object.class);
            return extractIdsFromPayload(response.getBody(), "followeeId", "id", "userId");
        } catch (Exception e) {
            logger.warn("Failed to resolve followees for userId={}: {}", userId, e.getMessage());
            return List.of();
        }
    }

    private List<Long> fetchFollowerIds(Long userId) {
        try {
            String url = followServiceUrl + "/follows/followers/" + userId;
            ResponseEntity<Object> response = restTemplate.exchange(url, HttpMethod.GET, null, Object.class);
            return extractIdsFromPayload(response.getBody(), "followerId", "id", "userId");
        } catch (Exception e) {
            logger.warn("Failed to resolve followers for userId={}: {}", userId, e.getMessage());
            return List.of();
        }
    }

    private List<Long> sanitizeUserIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<Long> unique = new LinkedHashSet<>();
        for (Long id : ids) {
            if (id != null && id > 0) {
                unique.add(id);
            }
        }
        return new ArrayList<>(unique);
    }

    private List<Long> extractIdsFromPayload(Object payload, String... possibleKeys) {
        Object data = payload;
        if (payload instanceof Map<?, ?> map && map.containsKey("data")) {
            data = map.get("data");
        }

        if (!(data instanceof List<?> list)) {
            return List.of();
        }

        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        for (Object entry : list) {
            try {
                if (entry instanceof Map<?, ?> mapEntry) {
                    Long id = null;
                    for (String key : possibleKeys) {
                        if (mapEntry.containsKey(key)) {
                            id = parseLongSafely(mapEntry.get(key)).orElse(null);
                            if (id != null) {
                                break;
                            }
                        }
                    }
                    if (id != null) {
                        ids.add(id);
                    }
                } else {
                    parseLongSafely(entry).ifPresent(ids::add);
                }
            } catch (Exception ignored) {
                logger.warn("Skipping malformed follow payload entry: {}", entry);
            }
        }
        return new ArrayList<>(ids);
    }

    private Optional<Long> parseLongSafely(Object value) {
        if (value == null) {
            return Optional.empty();
        }
        if (value instanceof Number number) {
            long parsed = number.longValue();
            return parsed > 0 ? Optional.of(parsed) : Optional.empty();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                long parsed = Long.parseLong(text.trim());
                return parsed > 0 ? Optional.of(parsed) : Optional.empty();
            } catch (NumberFormatException ignored) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    private void evictVisibilityAffectedFeeds(Long authorId) {
        feedCacheService.evictFeed(authorId);
        List<Long> followerIds = fetchFollowerIds(authorId);
        if (!followerIds.isEmpty()) {
            feedCacheService.evictFeeds(followerIds);
        }
    }

    private void triggerMentionNotifications(Long actorId, Long postId, String previousContent, String currentContent) {
        Set<String> currentMentions = extractMentions(currentContent);
        if (currentMentions.isEmpty()) {
            return;
        }

        if (previousContent != null) {
            currentMentions.removeAll(extractMentions(previousContent));
        }

        for (String username : currentMentions) {
            resolveUsernameToUserId(username).ifPresent(mentionedUserId -> {
                if (mentionedUserId.equals(actorId)) {
                    return;
                }
                sendMentionNotification(mentionedUserId, actorId, postId, username);
            });
        }
    }

    private Set<String> extractMentions(String content) {
        if (content == null || content.isBlank()) {
            return Set.of();
        }

        Matcher matcher = MENTION_PATTERN.matcher(content);
        LinkedHashSet<String> mentions = new LinkedHashSet<>();
        while (matcher.find() && mentions.size() < MAX_MENTIONS_PER_POST) {
            mentions.add(matcher.group(1).toLowerCase(Locale.ROOT));
        }
        return mentions;
    }

    private Optional<Long> resolveUsernameToUserId(String username) {
        try {
            String encoded = UriUtils.encodeQueryParam(username, StandardCharsets.UTF_8);
            String url = authServiceUrl + "/auth/search?q=" + encoded;
            ResponseEntity<Object> response = restTemplate.exchange(url, HttpMethod.GET, null, Object.class);
            return extractUserIdFromAuthSearch(response.getBody(), username);
        } catch (Exception e) {
            logger.warn("Failed to resolve mention @{}: {}", username, e.getMessage());
            return Optional.empty();
        }
    }

    private Optional<Long> extractUserIdFromAuthSearch(Object payload, String expectedUsername) {
        Object data = payload;
        if (payload instanceof Map<?, ?> map && map.containsKey("data")) {
            data = map.get("data");
        }

        if (!(data instanceof List<?> users)) {
            return Optional.empty();
        }

        for (Object user : users) {
            if (!(user instanceof Map<?, ?> userMap)) {
                continue;
            }

            Object usernameValue = userMap.get("username");
            if (!(usernameValue instanceof String foundUsername)) {
                continue;
            }

            if (!foundUsername.equalsIgnoreCase(expectedUsername)) {
                continue;
            }

            Optional<Long> userId = parseLongSafely(userMap.get("userId"));
            if (userId.isEmpty()) {
                userId = parseLongSafely(userMap.get("id"));
            }

            if (userId.isPresent()) {
                return userId;
            }
        }

        return Optional.empty();
    }

    private void sendMentionNotification(Long recipientId, Long actorId, Long postId, String username) {
        try {
            postEventPublisher.publishMentionNotification(
                    recipientId,
                    actorId,
                    postId,
                    "You were mentioned by @" + username + " in a post."
            );
        } catch (Exception e) {
            logger.warn("Failed to send mention notification for postId={}, recipientId={}: {}",
                    postId, recipientId, e.getMessage());
        }
    }

    private PostResponseDTO mapToDTO(Post post) {
        return PostResponseDTO.builder()
                .postId(post.getPostId())
                .authorId(post.getAuthorId())
                .content(post.getContent())
                .mediaUrls(post.getMediaUrls())
                .postType(post.getPostType())
                .visibility(post.getVisibility())
                .likesCount(post.getLikesCount())
                .commentsCount(post.getCommentsCount())
                .sharesCount(post.getSharesCount())
                .createdAt(post.getCreatedAt())
                .updatedAt(post.getUpdatedAt())
                .isFlagged(post.getIsFlagged())
                .moderationLabel(post.getModerationLabel())
                .moderationScore(post.getModerationScore())
                .moderationReviewed(post.getModerationReviewed())
                .build();
    }
}
