package com.connectsphere.post.serviceImpl;

import com.connectsphere.post.dto.PostResponseDTO;
import com.connectsphere.post.entity.Post;
import com.connectsphere.post.entity.PostType;
import com.connectsphere.post.entity.Visibility;
import com.connectsphere.post.messaging.PostEventPublisher;
import com.connectsphere.post.repository.PostRepository;
import com.connectsphere.post.service.ContentModerationService;
import com.connectsphere.post.service.FeedCacheService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PostServiceImplFeedCacheTest {

    @Mock
    private PostRepository postRepository;

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private FeedCacheService feedCacheService;

    @Mock
    private ContentModerationService contentModerationService;

    @Mock
    private PostEventPublisher postEventPublisher;

    @InjectMocks
    private PostServiceImpl postService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(postService, "followServiceUrl", "http://follow-service");
    }

    @Test
    void getFeedByUserId_returnsCachedFeed_withoutDatabaseCalls() {
        Long userId = 42L;
        List<PostResponseDTO> cachedFeed = List.of(
                PostResponseDTO.builder().postId(1001L).authorId(77L).content("cached").build()
        );
        when(feedCacheService.getCachedFeed(userId)).thenReturn(cachedFeed);

        List<PostResponseDTO> result = postService.getFeedByUserId(userId);

        assertEquals(1, result.size());
        assertEquals(1001L, result.get(0).getPostId());
        verify(feedCacheService).getCachedFeed(userId);
        verify(postRepository, never()).findPersonalizedFeedForUserWithoutFollowees(anyLong());
        verify(postRepository, never()).findPersonalizedFeedForUserWithFollowees(anyLong(), anyList());
        verify(feedCacheService, never()).cacheFeed(anyLong(), anyList());
    }

    @Test
    void getFeedByUserId_onCacheMiss_queriesDatabase_andBackfillsCache() {
        Long userId = 42L;
        when(feedCacheService.getCachedFeed(userId)).thenReturn(null);
        when(restTemplate.exchange(
                eq("http://follow-service/follows/following/" + userId),
                eq(HttpMethod.GET),
                isNull(),
                eq(Object.class)
        )).thenReturn(ResponseEntity.ok(List.of()));

        Post post = Post.builder()
                .postId(2001L)
                .authorId(userId)
                .content("from-db")
                .postType(PostType.TEXT)
                .visibility(Visibility.PUBLIC)
                .isDeleted(false)
                .build();
        when(postRepository.findPersonalizedFeedForUserWithoutFollowees(userId)).thenReturn(List.of(post));

        List<PostResponseDTO> result = postService.getFeedByUserId(userId);

        assertEquals(1, result.size());
        assertEquals(2001L, result.get(0).getPostId());
        verify(postRepository).findPersonalizedFeedForUserWithoutFollowees(userId);
        verify(postRepository, never()).findPersonalizedFeedForUserWithFollowees(anyLong(), anyList());
        verify(feedCacheService).cacheFeed(eq(userId), anyList());
    }
}
