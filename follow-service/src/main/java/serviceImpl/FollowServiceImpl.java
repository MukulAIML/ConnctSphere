package com.connectsphere.follow.serviceImpl;

import com.connectsphere.follow.dto.FollowRequestDTO;
import com.connectsphere.follow.dto.FollowResponseDTO;
import com.connectsphere.follow.entity.FollowEntity;
import com.connectsphere.follow.entity.FollowStatus;
import com.connectsphere.follow.exception.BadRequestException;
import com.connectsphere.follow.exception.ResourceNotFoundException;
import com.connectsphere.follow.repository.FollowRepository;
import com.connectsphere.follow.service.FollowService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import com.connectsphere.follow.dto.NotificationRequestDTO;

@Service
public class FollowServiceImpl implements FollowService {

    private static final Logger logger = LoggerFactory.getLogger(FollowServiceImpl.class);

    private final FollowRepository followRepository;
    private final RestTemplate restTemplate;

    @Value("${notification-service.url}")
    private String notificationServiceUrl;

    public FollowServiceImpl(FollowRepository followRepository, RestTemplate restTemplate) {
        this.followRepository = followRepository;
        this.restTemplate = restTemplate;
    }

    @Override
    @Transactional
    public FollowResponseDTO followUser(FollowRequestDTO requestDTO, Long followerId) {
        if (followerId.equals(requestDTO.getFolloweeId())) {
            throw new BadRequestException("You cannot follow yourself");
        }

        if (followRepository.existsByFollowerIdAndFolloweeId(followerId, requestDTO.getFolloweeId())) {
            FollowEntity existing = followRepository.findByFollowerIdAndFolloweeId(followerId, requestDTO.getFolloweeId())
                    .orElseThrow(() -> new ResourceNotFoundException("Follow relationship not found"));
            return mapToDTO(existing);
        }

        FollowEntity newFollow = FollowEntity.builder()
                .followerId(followerId)
                .followeeId(requestDTO.getFolloweeId())
                .status(FollowStatus.ACTIVE)
                .build();

        FollowEntity savedFollow = followRepository.save(newFollow);
        
        sendFollowNotification(savedFollow);
        
        return mapToDTO(savedFollow);
    }

    @Override
    @Transactional
    public void unfollowUser(Long followeeId, Long followerId) {
        if (!followRepository.existsByFollowerIdAndFolloweeId(followerId, followeeId)) {
            throw new ResourceNotFoundException("Follow relationship not found");
        }
        followRepository.deleteByFollowerIdAndFolloweeId(followerId, followeeId);
    }

    @Override
    public boolean isFollowing(Long followerId, Long followeeId) {
        return followRepository.existsByFollowerIdAndFolloweeId(followerId, followeeId);
    }

    @Override
    public List<FollowResponseDTO> getFollowers(Long userId) {
        // If someone follows ME, their followeeId is ME
        List<FollowEntity> followers = followRepository.findByFolloweeId(userId);
        return followers.stream().map(this::mapToDTO).collect(Collectors.toList());
    }

    @Override
    public List<FollowResponseDTO> getFollowing(Long userId) {
        // If I follow someone, my followerId is ME
        List<FollowEntity> following = followRepository.findByFollowerId(userId);
        return following.stream().map(this::mapToDTO).collect(Collectors.toList());
    }

    @Override
    public long getFollowerCount(Long userId) {
        return followRepository.countByFolloweeId(userId);
    }

    @Override
    public long getFollowingCount(Long userId) {
        return followRepository.countByFollowerId(userId);
    }

    @Override
    public List<Long> getMutualFollows(Long userId) {
        return followRepository.findMutualFollows(userId);
    }

    @Override
    public List<Long> getSuggestions(Long userId) {
        return followRepository.findSuggestions(userId);
    }

    private FollowResponseDTO mapToDTO(FollowEntity followEntity) {
        return FollowResponseDTO.builder()
                .followId(followEntity.getFollowId())
                .followerId(followEntity.getFollowerId())
                .followeeId(followEntity.getFolloweeId())
                .status(followEntity.getStatus())
                .createdAt(followEntity.getCreatedAt())
                .build();
    }

    private void sendFollowNotification(FollowEntity follow) {
        try {
            String url = notificationServiceUrl + "/notifications";
            
            NotificationRequestDTO notification = NotificationRequestDTO.builder()
                    .recipientId(follow.getFolloweeId())
                    .actorId(follow.getFollowerId())
                    .type("FOLLOW")
                    .message("Someone started following you")
                    .targetId(follow.getFollowerId())
                    .targetType("USER")
                    .build();

            restTemplate.exchange(url, HttpMethod.POST, new HttpEntity<>(notification), Void.class);
            logger.info("Successfully triggered notification for followId: {}", follow.getFollowId());
        } catch (Exception e) {
            logger.error("Failed to trigger notification for followId: {}: {}", follow.getFollowId(), e.getMessage());
        }
    }
}
