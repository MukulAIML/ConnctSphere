package com.connectsphere.follow.serviceImpl;

import com.connectsphere.follow.dto.FollowRequestDTO;
import com.connectsphere.follow.dto.FollowResponseDTO;
import com.connectsphere.follow.entity.FollowEntity;
import com.connectsphere.follow.entity.FollowStatus;
import com.connectsphere.follow.exception.BadRequestException;
import com.connectsphere.follow.exception.ResourceNotFoundException;
import com.connectsphere.follow.messaging.FollowEventPublisher;
import com.connectsphere.follow.repository.FollowRepository;
import com.connectsphere.follow.service.FollowService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class FollowServiceImpl implements FollowService {

    private static final Logger logger = LoggerFactory.getLogger(FollowServiceImpl.class);

    private final FollowRepository followRepository;
    private final FollowEventPublisher followEventPublisher;

    public FollowServiceImpl(FollowRepository followRepository, FollowEventPublisher followEventPublisher) {
        this.followRepository = followRepository;
        this.followEventPublisher = followEventPublisher;
    }

    @Override
    @Transactional
    public FollowResponseDTO followUser(FollowRequestDTO requestDTO, Long followerId) {
        validateAuthenticatedUserId(followerId);
        validateTargetUserId(requestDTO.getFolloweeId(), "followeeId");

        if (followerId.equals(requestDTO.getFolloweeId())) {
            throw new BadRequestException("You cannot follow yourself");
        }

        if (followRepository.existsByFollowerIdAndFolloweeId(followerId, requestDTO.getFolloweeId())) {
            FollowEntity existing = followRepository.findByFollowerIdAndFolloweeId(followerId, requestDTO.getFolloweeId())
                    .orElseThrow(() -> new ResourceNotFoundException("Follow relationship not found"));

            // Idempotent follow: if relation already exists, return existing.
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
        validateAuthenticatedUserId(followerId);
        validateTargetUserId(followeeId, "followeeId");

        if (!followRepository.existsByFollowerIdAndFolloweeId(followerId, followeeId)) {
            throw new ResourceNotFoundException("Follow relationship not found");
        }
        followRepository.deleteByFollowerIdAndFolloweeId(followerId, followeeId);
    }

    @Override
    public boolean isFollowing(Long followerId, Long followeeId) {
        validateAuthenticatedUserId(followerId);
        validateTargetUserId(followeeId, "followeeId");
        return followRepository.existsByFollowerIdAndFolloweeIdAndStatus(followerId, followeeId, FollowStatus.ACTIVE);
    }

    @Override
    public List<FollowResponseDTO> getFollowers(Long userId) {
        validateTargetUserId(userId, "userId");
        List<FollowEntity> followers = followRepository.findByFolloweeIdAndStatus(userId, FollowStatus.ACTIVE);
        return followers.stream().map(this::mapToDTO).collect(Collectors.toList());
    }

    @Override
    public List<FollowResponseDTO> getFollowing(Long userId) {
        validateTargetUserId(userId, "userId");
        List<FollowEntity> following = followRepository.findByFollowerIdAndStatus(userId, FollowStatus.ACTIVE);
        return following.stream().map(this::mapToDTO).collect(Collectors.toList());
    }

    @Override
    public long getFollowerCount(Long userId) {
        validateTargetUserId(userId, "userId");
        return followRepository.countByFolloweeIdAndStatus(userId, FollowStatus.ACTIVE);
    }

    @Override
    public long getFollowingCount(Long userId) {
        validateTargetUserId(userId, "userId");
        return followRepository.countByFollowerIdAndStatus(userId, FollowStatus.ACTIVE);
    }

    @Override
    public List<Long> getMutualFollows(Long userId) {
        validateTargetUserId(userId, "userId");
        return followRepository.findMutualFollows(userId);
    }

    @Override
    public List<Long> getSuggestions(Long userId) {
        validateTargetUserId(userId, "userId");
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
            followEventPublisher.publishFollowNotification(follow.getFolloweeId(), follow.getFollowerId());
            logger.info("Successfully triggered notification for followId: {}", follow.getFollowId());
        } catch (Exception e) {
            logger.error("Failed to trigger notification for followId: {}: {}", follow.getFollowId(), e.getMessage());
        }
    }

    private void validateAuthenticatedUserId(Long userId) {
        if (userId == null || userId <= 0) {
            throw new BadRequestException("Authenticated user context is missing or invalid");
        }
    }

    private void validateTargetUserId(Long userId, String fieldName) {
        if (userId == null || userId <= 0) {
            throw new BadRequestException(fieldName + " must be greater than 0");
        }
    }
}
