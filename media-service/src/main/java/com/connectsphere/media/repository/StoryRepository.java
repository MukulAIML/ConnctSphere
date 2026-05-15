package com.connectsphere.media.repository;

import com.connectsphere.media.entity.StoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface StoryRepository extends JpaRepository<StoryEntity, Long> {

    List<StoryEntity> findByIsActiveTrueAndExpiresAtAfterOrderByCreatedAtDesc(LocalDateTime now);

    List<StoryEntity> findByAuthorIdAndIsActiveTrueAndExpiresAtAfterOrderByCreatedAtDesc(
            Long authorId,
            LocalDateTime now
    );

    @Modifying
    @Query("""
            UPDATE StoryEntity s
            SET s.viewsCount = s.viewsCount + 1
            WHERE s.storyId = :storyId
              AND s.isActive = true
              AND s.expiresAt > :now
              AND s.authorId <> :viewerId
            """)
    int incrementViewsForActiveStory(
            @Param("storyId") Long storyId,
            @Param("viewerId") Long viewerId,
            @Param("now") LocalDateTime now
    );

    Optional<StoryEntity> findByStoryIdAndIsActiveTrueAndExpiresAtAfter(Long storyId, LocalDateTime now);

    @Modifying
    @Query("DELETE FROM StoryEntity s WHERE s.expiresAt <= :now")
    int deleteExpiredStories(@Param("now") LocalDateTime now);
}
