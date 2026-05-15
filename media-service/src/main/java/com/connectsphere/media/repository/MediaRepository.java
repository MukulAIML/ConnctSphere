package com.connectsphere.media.repository;

import com.connectsphere.media.entity.MediaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MediaRepository extends JpaRepository<MediaEntity, Long> {

    List<MediaEntity> findByLinkedPostIdAndIsDeletedFalse(Long linkedPostId);

    List<MediaEntity> findByUrlInAndIsDeletedFalse(List<String> urls);

    Optional<MediaEntity> findByUrlAndUploaderIdAndIsDeletedFalse(String url, Long uploaderId);
}
