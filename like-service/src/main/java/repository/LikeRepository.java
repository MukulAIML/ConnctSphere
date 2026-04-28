package com.connectsphere.like.repository;

import com.connectsphere.like.entity.LikeEntity;
import com.connectsphere.like.entity.ReactionType;
import com.connectsphere.like.entity.TargetType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LikeRepository extends JpaRepository<LikeEntity, Long> {

    Optional<LikeEntity> findByUserIdAndTargetIdAndTargetType(Long userId, Long targetId, TargetType targetType);

    List<LikeEntity> findByTargetIdAndTargetType(Long targetId, TargetType targetType);

    List<LikeEntity> findByUserId(Long userId);

    long countByTargetIdAndTargetType(Long targetId, TargetType targetType);

    long countByTargetIdAndTargetTypeAndReactionType(Long targetId, TargetType targetType, ReactionType reactionType);
}
