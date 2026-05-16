package com.connectsphere.search.repository;

import com.connectsphere.search.entity.PostHashtagEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PostHashtagRepository extends JpaRepository<PostHashtagEntity, Long> {

    List<PostHashtagEntity> findByPostId(Long postId);

    List<PostHashtagEntity> findByHashtagId(Long hashtagId);

    Optional<PostHashtagEntity> findByPostIdAndHashtagId(Long postId, Long hashtagId);

    long countByHashtagId(Long hashtagId);

    void deleteByPostId(Long postId);

    void deleteByPostIdAndHashtagIdIn(Long postId, List<Long> hashtagIds);
}
