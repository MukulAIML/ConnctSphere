package com.connectsphere.media.serviceImpl;

import com.connectsphere.media.dto.StoryRequestDTO;
import com.connectsphere.media.dto.StoryResponseDTO;
import com.connectsphere.media.entity.MediaEntity;
import com.connectsphere.media.entity.StoryEntity;
import com.connectsphere.media.exception.BadRequestException;
import com.connectsphere.media.exception.ResourceNotFoundException;
import com.connectsphere.media.exception.UnauthorizedAccessException;
import com.connectsphere.media.repository.MediaRepository;
import com.connectsphere.media.repository.StoryRepository;
import com.connectsphere.media.service.StoryService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class StoryServiceImpl implements StoryService {

    private static final Logger logger = LoggerFactory.getLogger(StoryServiceImpl.class);

    private final StoryRepository storyRepository;
    private final MediaRepository mediaRepository;

    public StoryServiceImpl(StoryRepository storyRepository, MediaRepository mediaRepository) {
        this.storyRepository = storyRepository;
        this.mediaRepository = mediaRepository;
    }

    @Override
    public StoryResponseDTO createStory(StoryRequestDTO requestDTO, Long authorId) {
        MediaEntity linkedMedia = mediaRepository.findByUrlAndUploaderIdAndIsDeletedFalse(
                        requestDTO.getMediaUrl(),
                        authorId
                )
                .orElseThrow(() -> new BadRequestException(
                        "Story media must be uploaded by the authenticated user before creating a story"));
        if (linkedMedia.getMediaType() != requestDTO.getMediaType()) {
            throw new BadRequestException("Story mediaType does not match uploaded media");
        }

        LocalDateTime now = currentTime();
        StoryEntity story = StoryEntity.builder()
                .authorId(authorId)
                .mediaUrl(requestDTO.getMediaUrl())
                .caption(requestDTO.getCaption())
                .mediaType(requestDTO.getMediaType())
                .viewsCount(0)
                .createdAt(now)
                .expiresAt(now.plusHours(24))
                .isActive(true)
                .build();

        StoryEntity savedStory = storyRepository.save(story);
        return mapToDTO(savedStory);
    }

    @Override
    public List<StoryResponseDTO> getActiveStories() {
        List<StoryEntity> stories = storyRepository
                .findByIsActiveTrueAndExpiresAtAfterOrderByCreatedAtDesc(currentTime());
        return stories.stream().map(this::mapToDTO).collect(Collectors.toList());
    }

    @Override
    public List<StoryResponseDTO> getStoriesByUser(Long userId) {
        List<StoryEntity> stories = storyRepository
                .findByAuthorIdAndIsActiveTrueAndExpiresAtAfterOrderByCreatedAtDesc(userId, currentTime());
        return stories.stream().map(this::mapToDTO).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void viewStory(Long storyId, Long viewerId) {
        LocalDateTime now = currentTime();
        StoryEntity story = storyRepository.findByStoryIdAndIsActiveTrueAndExpiresAtAfter(storyId, now)
                .orElseThrow(() -> new ResourceNotFoundException("Active story not found"));

        // Do not increment own story views.
        if (viewerId != null && !viewerId.equals(story.getAuthorId())) {
            int updated = storyRepository.incrementViewsForActiveStory(storyId, viewerId, now);
            if (updated == 0) {
                throw new ResourceNotFoundException("Active story not found");
            }
        }
    }

    @Override
    public void deleteStory(Long storyId, Long authorId) {
        StoryEntity story = storyRepository.findById(storyId)
                .orElseThrow(() -> new ResourceNotFoundException("Story not found"));

        if (!story.getAuthorId().equals(authorId)) {
            throw new UnauthorizedAccessException("You can only delete your own stories");
        }

        storyRepository.delete(story);
    }

    @Override
    @Transactional
    public void expireOldStories() {
        LocalDateTime now = currentTime();
        int deletedCount = storyRepository.deleteExpiredStories(now);
        logger.info("Executed story expiry cleanup at {}. Purged {} stories.", now, deletedCount);
    }

    private LocalDateTime currentTime() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }

    private StoryResponseDTO mapToDTO(StoryEntity story) {
        return StoryResponseDTO.builder()
                .storyId(story.getStoryId())
                .authorId(story.getAuthorId())
                .mediaUrl(story.getMediaUrl())
                .caption(story.getCaption())
                .mediaType(story.getMediaType())
                .viewsCount(story.getViewsCount())
                .createdAt(story.getCreatedAt())
                .expiresAt(story.getExpiresAt())
                .isActive(story.getIsActive())
                .build();
    }
}
