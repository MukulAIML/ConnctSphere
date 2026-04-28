package com.connectsphere.search.serviceImpl;

import com.connectsphere.search.dto.HashtagResponseDTO;
import com.connectsphere.search.dto.IndexRequestDTO;
import com.connectsphere.search.entity.HashtagEntity;
import com.connectsphere.search.entity.PostHashtagEntity;
import com.connectsphere.search.exception.ResourceNotFoundException;
import com.connectsphere.search.repository.HashtagRepository;
import com.connectsphere.search.repository.PostHashtagRepository;
import com.connectsphere.search.service.SearchService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class SearchServiceImpl implements SearchService {

    private final HashtagRepository hashtagRepository;
    private final PostHashtagRepository postHashtagRepository;
    private final RestTemplate restTemplate;

    private static final Pattern HASHTAG_PATTERN = Pattern.compile("#(\\w+)");

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
        // Find existing mappings for this post to handle updates properly
        List<PostHashtagEntity> existingMappings = postHashtagRepository.findByPostId(requestDTO.getPostId());
        Set<Long> existingHashtagIds = existingMappings.stream()
                .map(PostHashtagEntity::getHashtagId)
                .collect(Collectors.toSet());

        // Extract new hashtags
        Set<String> extractedTags = extractHashtags(requestDTO.getContent());

        LocalDateTime now = LocalDateTime.now();
        Set<Long> updatedHashtagIds = new HashSet<>();

        for (String tagText : extractedTags) {
            String lowerTag = tagText.toLowerCase();

            // Find or create hashtag
            HashtagEntity hashtag = hashtagRepository.findByTag(lowerTag).orElseGet(() -> {
                return hashtagRepository.save(HashtagEntity.builder()
                        .tag(lowerTag)
                        .postCount(0)
                        .lastUsedAt(now)
                        .build());
            });

            updatedHashtagIds.add(hashtag.getHashtagId());

            // If not already mapped to this post, map it and increment count
            if (!existingHashtagIds.contains(hashtag.getHashtagId())) {
                postHashtagRepository.save(PostHashtagEntity.builder()
                        .postId(requestDTO.getPostId())
                        .hashtagId(hashtag.getHashtagId())
                        .build());
                
                hashtag.setPostCount(hashtag.getPostCount() + 1);
            }
            
            hashtag.setLastUsedAt(now);
            hashtagRepository.save(hashtag);
        }

        // Handle removed tags during an update
        for (PostHashtagEntity mapping : existingMappings) {
            if (!updatedHashtagIds.contains(mapping.getHashtagId())) {
                postHashtagRepository.delete(mapping);
                
                hashtagRepository.findById(mapping.getHashtagId()).ifPresent(h -> {
                    h.setPostCount(Math.max(0, h.getPostCount() - 1));
                    hashtagRepository.save(h);
                });
            }
        }
    }

    @Override
    @Transactional
    public void removePostIndex(Long postId) {
        List<PostHashtagEntity> mappings = postHashtagRepository.findByPostId(postId);
        for (PostHashtagEntity mapping : mappings) {
            hashtagRepository.findById(mapping.getHashtagId()).ifPresent(h -> {
                h.setPostCount(Math.max(0, h.getPostCount() - 1));
                hashtagRepository.save(h);
            });
        }
        postHashtagRepository.deleteByPostId(postId);
    }

    @Override
    public List<Long> searchPostsByKeyword(String keyword) {
        try {
            String url = UriComponentsBuilder.fromHttpUrl(postServiceUrl + "/posts/search")
                    .queryParam("keyword", keyword)
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
        } catch (Exception ignored) {
            // Fallback to hashtag-based lookup when post-service is unavailable.
        }

        List<HashtagEntity> tags = hashtagRepository.findByTagContainingIgnoreCase(keyword);
        return tags.stream()
                .flatMap(tag -> postHashtagRepository.findByHashtagId(tag.getHashtagId()).stream())
                .map(PostHashtagEntity::getPostId)
                .distinct()
                .collect(Collectors.toList());
    }

    @Override
    public List<Long> searchUsersByKeyword(String keyword) {
        try {
            String url = UriComponentsBuilder.fromHttpUrl(authServiceUrl + "/auth/search")
                    .queryParam("q", keyword)
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
            return Collections.emptyList();
        }
    }

    @Override
    public List<HashtagResponseDTO> getHashtagsForPost(Long postId) {
        List<PostHashtagEntity> mappings = postHashtagRepository.findByPostId(postId);
        return mappings.stream()
                .map(mapping -> hashtagRepository.findById(mapping.getHashtagId()).orElse(null))
                .filter(Objects::nonNull)
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    @Override
    public List<HashtagResponseDTO> getTrendingHashtags() {
        return hashtagRepository.findTop10ByOrderByPostCountDesc().stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    @Override
    public HashtagResponseDTO getHashtagByTag(String tag) {
        HashtagEntity hashtag = hashtagRepository.findByTag(tag.toLowerCase())
                .orElseThrow(() -> new ResourceNotFoundException("Hashtag not found"));
        return mapToDTO(hashtag);
    }

    @Override
    public List<HashtagResponseDTO> searchHashtags(String keyword) {
        return hashtagRepository.findByTagContainingIgnoreCase(keyword).stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    @Override
    public Integer getHashtagCount(String tag) {
        return hashtagRepository.findByTag(tag.toLowerCase())
                .map(HashtagEntity::getPostCount)
                .orElse(0);
    }
    
    @Override
    public List<Long> getPostsByHashtag(String tag) {
        HashtagEntity hashtag = hashtagRepository.findByTag(tag.toLowerCase())
                .orElseThrow(() -> new ResourceNotFoundException("Hashtag not found"));
                
        return postHashtagRepository.findByHashtagId(hashtag.getHashtagId()).stream()
                .map(PostHashtagEntity::getPostId)
                .distinct()
                .collect(Collectors.toList());
    }

    private Set<String> extractHashtags(String content) {
        Set<String> tags = new HashSet<>();
        if (content == null || content.isEmpty()) {
            return tags;
        }

        Matcher matcher = HASHTAG_PATTERN.matcher(content);
        while (matcher.find()) {
            tags.add(matcher.group(1)); // Extract group 1 (without the '#')
        }
        return tags;
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
