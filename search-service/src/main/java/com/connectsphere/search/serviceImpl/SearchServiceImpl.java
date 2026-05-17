package com.connectsphere.search.serviceImpl;

import com.connectsphere.search.dto.HashtagResponseDTO;
import com.connectsphere.search.dto.IndexRequestDTO;

import com.connectsphere.search.entity.HashtagEntity;
import com.connectsphere.search.entity.PostHashtagEntity;
import com.connectsphere.search.exception.BadRequestException;
import com.connectsphere.search.exception.ResourceNotFoundException;

import com.connectsphere.search.repository.HashtagRepository;
import com.connectsphere.search.repository.PostHashtagRepository;
import com.connectsphere.search.service.SearchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class SearchServiceImpl implements SearchService {

    private static final Logger logger = LoggerFactory.getLogger(SearchServiceImpl.class);

    private static final Pattern HASHTAG_PATTERN = Pattern.compile("(?<![A-Za-z0-9_])#([A-Za-z0-9_]{1,100})");

    private final HashtagRepository hashtagRepository;
    private final PostHashtagRepository postHashtagRepository;
    private final RestTemplate restTemplate;

    @Value("${auth-service.url}")
    private String authServiceUrl;

    @Value("${post-service.url}")
    private String postServiceUrl;

    public SearchServiceImpl(
            HashtagRepository hashtagRepository,
            PostHashtagRepository postHashtagRepository,
            RestTemplate restTemplate
    ) {
        this.hashtagRepository = hashtagRepository;
        this.postHashtagRepository = postHashtagRepository;
        this.restTemplate = restTemplate;
    }

    @Override
    @Transactional
    public void indexPost(IndexRequestDTO requestDTO) {
        validateIndexRequest(requestDTO);

        Long postId = requestDTO.getPostId();
        LocalDateTime now = LocalDateTime.now();
        Set<String> extractedTags = extractHashtags(requestDTO.getContent());

        List<PostHashtagEntity> currentMappings = postHashtagRepository.findByPostId(postId);
        Set<Long> currentHashtagIds = currentMappings.stream()
                .map(PostHashtagEntity::getHashtagId)
                .collect(Collectors.toSet());

        Map<String, HashtagEntity> desiredHashtags = new HashMap<>();
        for (String tag : extractedTags) {
            HashtagEntity hashtag = findOrCreateHashtag(tag, now);
            desiredHashtags.put(tag, hashtag);
        }

        Set<Long> desiredHashtagIds = desiredHashtags.values().stream()
                .map(HashtagEntity::getHashtagId)
                .collect(Collectors.toSet());

        Set<Long> toAdd = new HashSet<>(desiredHashtagIds);
        toAdd.removeAll(currentHashtagIds);

        if (!toAdd.isEmpty()) {
            List<PostHashtagEntity> newMappings = new ArrayList<>();
            for (Long hashtagId : toAdd) {
                newMappings.add(PostHashtagEntity.builder()
                        .postId(postId)
                        .hashtagId(hashtagId)
                        .build());
            }
            try {
                postHashtagRepository.saveAll(newMappings);
            } catch (DataIntegrityViolationException ex) {
                logger.warn("Duplicate post-hashtag mapping detected during concurrent indexing for postId={}", postId);
            }
        }

        Set<Long> toRemove = new HashSet<>(currentHashtagIds);
        toRemove.removeAll(desiredHashtagIds);

        if (!toRemove.isEmpty()) {
            postHashtagRepository.deleteByPostIdAndHashtagIdIn(postId, new ArrayList<>(toRemove));
        }

        // Touch all hashtags still present in this post so trending reflects latest activity.
        for (HashtagEntity hashtag : desiredHashtags.values()) {
            hashtag.setLastUsedAt(now);
            hashtagRepository.save(hashtag);
        }

        Set<Long> affectedHashtagIds = new HashSet<>();
        affectedHashtagIds.addAll(toAdd);
        affectedHashtagIds.addAll(toRemove);
        affectedHashtagIds.addAll(desiredHashtagIds);

        for (Long hashtagId : affectedHashtagIds) {
            syncHashtagState(hashtagId);
        }
    }

    @Override
    @Transactional
    public void removePostIndex(Long postId) {
        if (postId == null || postId <= 0) {
            throw new BadRequestException("postId must be greater than 0");
        }

        List<PostHashtagEntity> mappings = postHashtagRepository.findByPostId(postId);
        if (mappings.isEmpty()) {
            return;
        }

        Set<Long> affectedHashtagIds = mappings.stream()
                .map(PostHashtagEntity::getHashtagId)
                .collect(Collectors.toSet());

        postHashtagRepository.deleteByPostId(postId);

        for (Long hashtagId : affectedHashtagIds) {
            syncHashtagState(hashtagId);
        }
    }

    @Override
    public List<Long> searchPostsByKeyword(String keyword) {
        String normalizedKeyword = normalizeSearchKeyword(keyword);
        if (normalizedKeyword.isEmpty()) {
            return Collections.emptyList();
        }

        try {
            String url = UriComponentsBuilder.fromHttpUrl(postServiceUrl + "/posts/search")
                    .queryParam("keyword", normalizedKeyword)
                    .toUriString();
            ResponseEntity<List> response = restTemplate.exchange(url, HttpMethod.GET, null, List.class);
            List<?> body = response.getBody();
            if (body != null && !body.isEmpty()) {
                return body.stream()
                        .filter(Map.class::isInstance)
                        .map(row -> (Map<?, ?>) row)
                        .map(post -> post.get("postId"))
                        .filter(Objects::nonNull)
                        .map(id -> Long.valueOf(id.toString()))
                        .distinct()
                        .collect(Collectors.toList());
            }
        } catch (Exception e) {
            logger.warn("post-service unavailable, falling back to hashtag search: {}", e.getMessage());
        }

        String tagKeyword = normalizedKeyword.startsWith("#")
                ? normalizeHashtagKeyword(normalizedKeyword)
                : normalizedKeyword;
        if (tagKeyword.isEmpty()) {
            return Collections.emptyList();
        }

        List<HashtagEntity> hashtags = hashtagRepository
                .findByTagContainingIgnoreCaseAndPostCountGreaterThan(tagKeyword, 0);

        return hashtags.stream()
                .flatMap(hashtag -> postHashtagRepository.findByHashtagId(hashtag.getHashtagId()).stream())
                .map(PostHashtagEntity::getPostId)
                .distinct()
                .collect(Collectors.toList());
    }

    @Override
    public List<Long> searchUsersByKeyword(String keyword) {
        String normalizedKeyword = normalizeSearchKeyword(keyword);
        if (normalizedKeyword.isEmpty()) {
            return Collections.emptyList();
        }

        try {
            String url = UriComponentsBuilder.fromHttpUrl(authServiceUrl + "/auth/search")
                    .queryParam("q", normalizedKeyword)
                    .toUriString();
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, null, Map.class);
            Object data = response.getBody() == null ? null : response.getBody().get("data");
            if (!(data instanceof List<?> users)) {
                return Collections.emptyList();
            }
            return users.stream()
                    .filter(Map.class::isInstance)
                    .map(Map.class::cast)
                    .map(user -> user.get("userId"))
                    .filter(Objects::nonNull)
                    .map(id -> Long.valueOf(id.toString()))
                    .distinct()
                    .collect(Collectors.toList());
        } catch (Exception ex) {
            logger.warn("auth-service unavailable during user search: {}", ex.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    public List<HashtagResponseDTO> getHashtagsForPost(Long postId) {
        if (postId == null || postId <= 0) {
            throw new BadRequestException("postId must be greater than 0");
        }

        List<PostHashtagEntity> mappings = postHashtagRepository.findByPostId(postId);
        if (mappings.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> hashtagIds = mappings.stream()
                .map(PostHashtagEntity::getHashtagId)
                .distinct()
                .collect(Collectors.toList());

        Map<Long, HashtagEntity> hashtagsById = hashtagRepository.findAllById(hashtagIds).stream()
                .collect(Collectors.toMap(HashtagEntity::getHashtagId, hashtag -> hashtag));

        return hashtagIds.stream()
                .map(hashtagsById::get)
                .filter(Objects::nonNull)
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    @Override
    public List<HashtagResponseDTO> getTrendingHashtags() {
        return hashtagRepository.findTop10ByPostCountGreaterThanOrderByPostCountDescLastUsedAtDesc(0)
                .stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    @Override
    public HashtagResponseDTO getHashtagByTag(String tag) {
        String normalizedTag = normalizeHashtagKeyword(tag);
        HashtagEntity hashtag = hashtagRepository.findByTag(normalizedTag)
                .filter(entity -> entity.getPostCount() != null && entity.getPostCount() > 0)
                .orElseThrow(() -> new ResourceNotFoundException("Hashtag not found"));
        return mapToDTO(hashtag);
    }

    @Override
    public List<HashtagResponseDTO> searchHashtags(String keyword) {
        String normalizedKeyword = normalizeHashtagKeyword(keyword);
        if (normalizedKeyword.isEmpty()) {
            return Collections.emptyList();
        }

        return hashtagRepository.findByTagContainingIgnoreCaseAndPostCountGreaterThan(normalizedKeyword, 0)
                .stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    @Override
    public Integer getHashtagCount(String tag) {
        String normalizedTag = normalizeHashtagKeyword(tag);
        return hashtagRepository.findByTag(normalizedTag)
                .map(HashtagEntity::getPostCount)
                .orElse(0);
    }

    @Override
    public List<Long> getPostsByHashtag(String tag) {
        String normalizedTag = normalizeHashtagKeyword(tag);
        HashtagEntity hashtag = hashtagRepository.findByTag(normalizedTag)
                .filter(entity -> entity.getPostCount() != null && entity.getPostCount() > 0)
                .orElseThrow(() -> new ResourceNotFoundException("Hashtag not found"));

        return postHashtagRepository.findByHashtagId(hashtag.getHashtagId()).stream()
                .map(PostHashtagEntity::getPostId)
                .distinct()
                .collect(Collectors.toList());
    }

    private void validateIndexRequest(IndexRequestDTO requestDTO) {
        if (requestDTO == null || requestDTO.getPostId() == null || requestDTO.getPostId() <= 0) {
            throw new BadRequestException("postId must be greater than 0");
        }
    }

    private String normalizeSearchKeyword(String keyword) {
        return keyword == null ? "" : keyword.trim();
    }

    private HashtagEntity findOrCreateHashtag(String tag, LocalDateTime now) {
        return hashtagRepository.findByTag(tag).orElseGet(() -> {
            try {
                return hashtagRepository.save(HashtagEntity.builder()
                        .tag(tag)
                        .postCount(0)
                        .lastUsedAt(now)
                        .build());
            } catch (DataIntegrityViolationException ex) {
                // Another transaction created the same hashtag concurrently.
                return hashtagRepository.findByTag(tag).orElseThrow(() -> ex);
            }
        });
    }

    private String normalizeHashtagKeyword(String keyword) {
        if (keyword == null) {
            return "";
        }
        String normalized = keyword.trim().toLowerCase();
        if (normalized.startsWith("#")) {
            normalized = normalized.substring(1);
        }
        return normalized;
    }

    private Set<String> extractHashtags(String content) {
        Set<String> tags = new LinkedHashSet<>();
        if (content == null || content.isBlank()) {
            return tags;
        }

        Matcher matcher = HASHTAG_PATTERN.matcher(content);
        while (matcher.find()) {
            String normalized = normalizeHashtagKeyword(matcher.group(1));
            if (!normalized.isEmpty()) {
                tags.add(normalized);
            }
        }
        return tags;
    }

    private void syncHashtagState(Long hashtagId) {
        long mappingCount = postHashtagRepository.countByHashtagId(hashtagId);
        Optional<HashtagEntity> hashtagOpt = hashtagRepository.findById(hashtagId);

        if (hashtagOpt.isEmpty()) {
            return;
        }

        HashtagEntity hashtag = hashtagOpt.get();
        if (mappingCount <= 0) {
            hashtagRepository.delete(hashtag);
            return;
        }

        hashtag.setPostCount((int) mappingCount);
        HashtagEntity saved = hashtagRepository.save(hashtag);
    }

    private HashtagResponseDTO mapToDTO(HashtagEntity hashtag) {
        return HashtagResponseDTO.builder()
                .hashtagId(hashtag.getHashtagId())
                .tag(hashtag.getTag())
                .postCount(hashtag.getPostCount())
                .lastUsedAt(hashtag.getLastUsedAt())
                .build();
    }
}
