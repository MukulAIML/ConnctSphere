package com.connectsphere.media.repository;

import com.connectsphere.media.entity.StoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface StoryRepository extends JpaRepository<StoryEntity, Long> {

    List<StoryEntity> findByIsActiveTrueOrderByCreatedAtDesc();

    List<StoryEntity> findByAuthorIdAndIsActiveTrueOrderByCreatedAtDesc(Long authorId);

    @Modifying
    @Query("UPDATE StoryEntity s SET s.isActive = false WHERE s.isActive = true AND s.expiresAt < :now")
    void markExpiredStoriesAsInactive(@Param("now") LocalDateTime now);
}
