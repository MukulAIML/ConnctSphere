package com.connectsphere.media.repository;

import com.connectsphere.media.entity.MediaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MediaRepository extends JpaRepository<MediaEntity, Long> {

    List<MediaEntity> findByLinkedPostIdAndIsDeletedFalse(Long linkedPostId);

    List<MediaEntity> findByUrlInAndIsDeletedFalse(List<String> urls);
}
