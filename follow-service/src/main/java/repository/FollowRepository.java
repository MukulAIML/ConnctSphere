package com.connectsphere.follow.repository;

import com.connectsphere.follow.entity.FollowEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FollowRepository extends JpaRepository<FollowEntity, Long> {

    Optional<FollowEntity> findByFollowerIdAndFolloweeId(Long followerId, Long followeeId);

    boolean existsByFollowerIdAndFolloweeId(Long followerId, Long followeeId);

    List<FollowEntity> findByFolloweeId(Long followeeId);

    List<FollowEntity> findByFollowerId(Long followerId);

    long countByFolloweeId(Long followeeId);

    long countByFollowerId(Long followerId);

    void deleteByFollowerIdAndFolloweeId(Long followerId, Long followeeId);

    @Query("SELECT f.followeeId FROM FollowEntity f WHERE f.followerId = :userId " +
           "AND f.followeeId IN (SELECT f2.followerId FROM FollowEntity f2 WHERE f2.followeeId = :userId)")
    List<Long> findMutualFollows(@Param("userId") Long userId);

    @Query("SELECT DISTINCT f2.followeeId FROM FollowEntity f1 " +
           "JOIN FollowEntity f2 ON f1.followeeId = f2.followerId " +
           "WHERE f1.followerId = :userId " +
           "AND f2.followeeId != :userId " +
           "AND f2.followeeId NOT IN (SELECT f3.followeeId FROM FollowEntity f3 WHERE f3.followerId = :userId)")
    List<Long> findSuggestions(@Param("userId") Long userId);
}
