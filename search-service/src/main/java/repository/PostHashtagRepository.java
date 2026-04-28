package com.connectsphere.search.repository;

import com.connectsphere.search.entity.PostHashtagEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PostHashtagRepository extends JpaRepository<PostHashtagEntity, Long> {

    List<PostHashtagEntity> findByPostId(Long postId);

    List<PostHashtagEntity> findByHashtagId(Long hashtagId);

    void deleteByPostId(Long postId);
}
