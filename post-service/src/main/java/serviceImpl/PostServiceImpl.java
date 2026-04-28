package com.connectsphere.post.serviceImpl;

import com.connectsphere.post.dto.PostRequestDTO;
import com.connectsphere.post.dto.PostResponseDTO;
import com.connectsphere.post.entity.Post;
import com.connectsphere.post.entity.Visibility;
import com.connectsphere.post.exception.ResourceNotFoundException;
import com.connectsphere.post.exception.UnauthorizedAccessException;
import com.connectsphere.post.repository.PostRepository;
import com.connectsphere.post.service.PostService;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

import com.connectsphere.post.dto.IndexRequestDTO;
import com.connectsphere.post.dto.MediaUrlsUpdateDTO;

@Service
public class PostServiceImpl implements PostService {

    private static final Logger logger = LoggerFactory.getLogger(PostServiceImpl.class);

    private final PostRepository postRepository;
    private final RestTemplate restTemplate;

    @Value("${search-service.url}")
    private String searchServiceUrl;

    @Value("${follow-service.url}")
    private String followServiceUrl;

    @Value("${media-service.url}")
    private String mediaServiceUrl;

    public PostServiceImpl(PostRepository postRepository, RestTemplate restTemplate) {
        this.postRepository = postRepository;
        this.restTemplate = restTemplate;
    }

    // ================= CREATE =================
    @Override
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
                .build();

        Post saved = postRepository.save(post);
        triggerSearchIndex(saved);

        return mapToDTO(saved);
    }

    // ================= GET =================
    @Override
    public PostResponseDTO getPostById(Long postId) {
        return mapToDTO(getPostEntityById(postId));
    }

    @Override
    public List<PostResponseDTO> getPostsByUser(Long userId) {
        return postRepository
                .findByAuthorIdAndIsDeletedFalseOrderByCreatedAtDesc(userId)
                .stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    // ================= UPDATE =================
    @Override
    public PostResponseDTO updatePost(Long postId, PostRequestDTO dto, Long authorId) {

        Post post = getPostEntityById(postId);
        validateAuthor(post, authorId);

        post.setContent(dto.getContent());
        post.setMediaUrls(dto.getMediaUrls());
        post.setPostType(dto.getPostType());
        post.setVisibility(dto.getVisibility());

        Post updated = postRepository.save(post);
        triggerSearchIndex(updated);

        return mapToDTO(updated);
    }

    // ================= DELETE =================
    @Override
    public void deletePost(Long postId, Long authorId) {

        Post post = getPostEntityById(postId);
        validateAuthor(post, authorId);

        post.setIsDeleted(true);
        postRepository.save(post);

        triggerSearchRemove(postId);
        triggerMediaSoftDelete(post.getMediaUrls());
    }

    // ================= SEARCH =================
    @Override
    public List<PostResponseDTO> searchPosts(String keyword) {
        return postRepository.searchPostsByKeyword(keyword)
                .stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    // ================= GENERATE FEED (by followee list) =================
    @Override
    public List<PostResponseDTO> generateFeed(List<Long> followeeIds) {
        if (followeeIds == null || followeeIds.isEmpty()) {
            return List.of();
        }
        return postRepository.findFeedByFolloweeIds(followeeIds)
                .stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    // ================= CHANGE VISIBILITY =================
    @Override
    public PostResponseDTO changeVisibility(Long postId, com.connectsphere.post.entity.Visibility visibility, Long authorId) {
        Post post = getPostEntityById(postId);
        validateAuthor(post, authorId);
        post.setVisibility(visibility);
        return mapToDTO(postRepository.save(post));
    }

    // ================= ENGAGEMENT =================
    @Override
    public PostResponseDTO incrementLike(Long postId) {
        Post post = getPostEntityById(postId);
        post.setLikesCount(post.getLikesCount() + 1);
        return mapToDTO(postRepository.save(post));
    }

    @Override
    public PostResponseDTO decrementLike(Long postId) {
        Post post = getPostEntityById(postId);
        if (post.getLikesCount() > 0) {
            post.setLikesCount(post.getLikesCount() - 1);
        }
        return mapToDTO(postRepository.save(post));
    }

    @Override
    public PostResponseDTO incrementComment(Long postId) {
        Post post = getPostEntityById(postId);
        post.setCommentsCount(post.getCommentsCount() + 1);
        return mapToDTO(postRepository.save(post));
    }

    @Override
    public PostResponseDTO decrementComment(Long postId) {
        Post post = getPostEntityById(postId);
        if (post.getCommentsCount() > 0) {
            post.setCommentsCount(post.getCommentsCount() - 1);
        }
        return mapToDTO(postRepository.save(post));
    }

    // ================= 🔥 FINAL FIXED FEED =================
    @Override
    public List<PostResponseDTO> getFeedByUserId(Long userId) {

        logger.info("Generating feed for user: {}", userId);

        List<Long> authorIds = new ArrayList<>();

        try {
            String url = followServiceUrl + "/follows/following/" + userId;

            ResponseEntity<List> response =
                    restTemplate.exchange(url, HttpMethod.GET, null, List.class);

            if (response.getBody() != null) {

                for (Object obj : response.getBody()) {
                    try {
                        Map map = (Map) obj;

                        Object id = map.get("followeeId");
                        if (id == null) id = map.get("id");
                        if (id == null) id = map.get("userId");

                        if (id != null) {
                            authorIds.add(Long.valueOf(id.toString()));
                        }

                    } catch (Exception e) {
                        logger.warn("Skipping invalid follow object: {}", obj);
                    }
                }
            }

        } catch (Exception e) {
            logger.warn("Follow service failed → fallback activated");
        }

        // ✅ ALWAYS INCLUDE SELF
        if (!authorIds.contains(userId)) {
            authorIds.add(userId);
        }

        logger.info("Feed authors: {}", authorIds);

        List<Post> posts;

        try {
            posts = postRepository.findFeedByFolloweeIds(authorIds);
        } catch (Exception e) {
            logger.error("DB fetch failed → fallback");
            posts = new ArrayList<>();
        }

        if (posts == null || posts.isEmpty()) {

            logger.info("Feed empty → returning ALL POSTS");

            posts = postRepository.findAll().stream()
                    .filter(p -> !p.getIsDeleted())
                    .sorted(Comparator.comparing(Post::getCreatedAt).reversed())
                    .collect(Collectors.toList());
        }

        return posts.stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    // ================= ALL POSTS =================
    @Override
    public List<PostResponseDTO> getAllPosts() {
        return postRepository.findAll()
                .stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    // ================= MEDIA =================
    @Override
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

    // ================= SEARCH SYNC =================
    private void triggerSearchIndex(Post post) {
        try {
            if (post.getContent() == null || post.getContent().isEmpty()) return;

            String url = searchServiceUrl + "/search/index";

            restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    new HttpEntity<>(new IndexRequestDTO(post.getPostId(), post.getContent())),
                    Void.class
            );

        } catch (Exception e) {
            logger.warn("Search indexing skipped");
        }
    }

    private void triggerSearchRemove(Long postId) {
        try {
            String url = searchServiceUrl + "/search/remove/" + postId;
            restTemplate.exchange(url, HttpMethod.DELETE, null, Void.class);
        } catch (Exception e) {
            logger.warn("Search remove skipped");
        }
    }

    private void triggerMediaSoftDelete(List<String> mediaUrls) {
        if (mediaUrls == null || mediaUrls.isEmpty()) {
            return;
        }
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

    private void validateAuthor(Post post, Long authorId) {
        if (!post.getAuthorId().equals(authorId)) {
            throw new UnauthorizedAccessException("Not authorized");
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
                .build();
    }
}
