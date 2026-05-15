package com.connectsphere.media.service;

import com.connectsphere.media.dto.StoryRequestDTO;
import com.connectsphere.media.dto.StoryResponseDTO;

import java.util.List;

public interface StoryService {
    StoryResponseDTO createStory(StoryRequestDTO requestDTO, Long authorId);
    List<StoryResponseDTO> getActiveStories();
    List<StoryResponseDTO> getStoriesByUser(Long userId);
    void viewStory(Long storyId, Long viewerId);
    void deleteStory(Long storyId, Long authorId);
    void expireOldStories();
}
