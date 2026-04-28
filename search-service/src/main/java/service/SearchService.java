package com.connectsphere.search.service;

import com.connectsphere.search.dto.HashtagResponseDTO;
import com.connectsphere.search.dto.IndexRequestDTO;

import java.util.List;

public interface SearchService {
    void indexPost(IndexRequestDTO requestDTO);
    void removePostIndex(Long postId);
    List<Long> searchPostsByKeyword(String keyword);
    List<Long> searchUsersByKeyword(String keyword);
    List<HashtagResponseDTO> getHashtagsForPost(Long postId);
    List<HashtagResponseDTO> getTrendingHashtags();
    HashtagResponseDTO getHashtagByTag(String tag);
    List<HashtagResponseDTO> searchHashtags(String keyword);
    Integer getHashtagCount(String tag);
    List<Long> getPostsByHashtag(String tag);
}
