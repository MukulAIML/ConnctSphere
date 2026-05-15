package com.connectsphere.follow.repository;

import com.connectsphere.follow.entity.FollowEntity;
import com.connectsphere.follow.entity.FollowStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FollowRepository extends JpaRepository<FollowEntity, Long> {

    Optional<FollowEntity> findByFollowerIdAndFolloweeId(Long followerId, Long followeeId);
    
    Optional<FollowEntity> findByFollowerIdAndFolloweeIdAndStatus(Long followerId, Long followeeId, FollowStatus status);

    boolean existsByFollowerIdAndFolloweeId(Long followerId, Long followeeId);
    
    boolean existsByFollowerIdAndFolloweeIdAndStatus(Long followerId, Long followeeId, FollowStatus status);

    List<FollowEntity> findByFolloweeId(Long followeeId);
    
    List<FollowEntity> findByFolloweeIdAndStatus(Long followeeId, FollowStatus status);

    List<FollowEntity> findByFollowerId(Long followerId);
    
    List<FollowEntity> findByFollowerIdAndStatus(Long followerId, FollowStatus status);

    long countByFolloweeId(Long followeeId);
    
    long countByFolloweeIdAndStatus(Long followeeId, FollowStatus status);

    long countByFollowerId(Long followerId);
    
    long countByFollowerIdAndStatus(Long followerId, FollowStatus status);

    void deleteByFollowerIdAndFolloweeId(Long followerId, Long followeeId);

    @Query("""
            SELECT f.followeeId
            FROM FollowEntity f
            WHERE f.followerId = :userId
              AND f.status = com.connectsphere.follow.entity.FollowStatus.ACTIVE
              AND f.followeeId IN (
                    SELECT f2.followerId
                    FROM FollowEntity f2
                    WHERE f2.followeeId = :userId
                      AND f2.status = com.connectsphere.follow.entity.FollowStatus.ACTIVE
              )
            ORDER BY f.followeeId ASC
            """)
    List<Long> findMutualFollows(@Param("userId") Long userId);

    @Query("""
            SELECT f2.followeeId
            FROM FollowEntity f1
            JOIN FollowEntity f2 ON f1.followeeId = f2.followerId
            WHERE f1.followerId = :userId
              AND f1.status = com.connectsphere.follow.entity.FollowStatus.ACTIVE
              AND f2.status = com.connectsphere.follow.entity.FollowStatus.ACTIVE
              AND f2.followeeId <> :userId
              AND f2.followeeId NOT IN (
                    SELECT f3.followeeId
                    FROM FollowEntity f3
                    WHERE f3.followerId = :userId
                      AND f3.status = com.connectsphere.follow.entity.FollowStatus.ACTIVE
              )
            GROUP BY f2.followeeId
            ORDER BY COUNT(f2.followeeId) DESC, f2.followeeId ASC
            """)
    List<Long> findSuggestions(@Param("userId") Long userId);
}
