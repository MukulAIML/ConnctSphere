package com.connectsphere.search.repository;

import com.connectsphere.search.entity.HashtagDocument;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface HashtagElasticsearchRepository extends ElasticsearchRepository<HashtagDocument, String> {

    Optional<HashtagDocument> findByTag(String tag);

    List<HashtagDocument> findByTagContainingIgnoreCase(String keyword);

    List<HashtagDocument> findTop10ByOrderByPostCountDesc();
}
