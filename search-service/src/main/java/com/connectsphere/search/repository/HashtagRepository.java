package com.connectsphere.search.repository;

import com.connectsphere.search.entity.HashtagEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface HashtagRepository extends JpaRepository<HashtagEntity, Long> {

    Optional<HashtagEntity> findByTag(String tag);

    List<HashtagEntity> findByTagContainingIgnoreCaseAndPostCountGreaterThan(String keyword, Integer postCount);

    List<HashtagEntity> findTop10ByPostCountGreaterThanOrderByPostCountDescLastUsedAtDesc(Integer postCount);
}
