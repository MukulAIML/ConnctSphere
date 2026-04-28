package com.connectsphere.media;

import com.connectsphere.media.entity.StoryEntity;
import com.connectsphere.media.repository.MediaRepository;
import com.connectsphere.media.repository.StoryRepository;
import com.connectsphere.media.service.StoryService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:mediadb;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
                "spring.jpa.show-sql=false",
                "eureka.client.enabled=false",
                "spring.cloud.discovery.enabled=false"
        }
)
@AutoConfigureMockMvc
class MediaStoryServiceIntegrationTest {

    private static final String JWT_SECRET = "ConnectSphereSecretKey2026ConnectSphereSecretKey2026ConnectSphereSecretKey";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MediaRepository mediaRepository;

    @Autowired
    private StoryRepository storyRepository;

    @Autowired
    private StoryService storyService;

    @MockBean
    private RestTemplate restTemplate;

    @BeforeEach
    void setup() {
        mediaRepository.deleteAll();
        storyRepository.deleteAll();
    }

    @Test
    void uploadMedia_returnsUrl() throws Exception {
        mockMvc.perform(post("/media")
                        .header("Authorization", bearer(11L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "url":"https://cdn.example.com/media/a1.jpg",
                                  "mediaType":"IMAGE",
                                  "sizeKb":512,
                                  "mimeType":"image/jpeg",
                                  "linkedPostId":1001
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.url").value("https://cdn.example.com/media/a1.jpg"));

        assertEquals(1, mediaRepository.findByLinkedPostIdAndIsDeletedFalse(1001L).size());
    }

    @Test
    void getMediaByPost_returnsMediaList() throws Exception {
        uploadMedia(12L, "https://cdn.example.com/media/p2001-1.jpg", 2001L);
        uploadMedia(12L, "https://cdn.example.com/media/p2002-1.jpg", 2002L);

        mockMvc.perform(get("/media/post/{postId}", 2001L)
                        .header("Authorization", bearer(12L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].linkedPostId").value(2001))
                .andExpect(jsonPath("$.data[0].url").value("https://cdn.example.com/media/p2001-1.jpg"));
    }

    @Test
    void createStory_createsStory() throws Exception {
        mockMvc.perform(post("/stories")
                        .header("Authorization", bearer(21L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(storyJson("https://cdn.example.com/stories/s1.jpg", "hello", "IMAGE")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.authorId").value(21))
                .andExpect(jsonPath("$.data.isActive").value(true));

        assertEquals(1, storyRepository.findAll().size());
    }

    @Test
    void viewStories_returnsActiveStories() throws Exception {
        createStory(31L, "https://cdn.example.com/stories/a.jpg");
        createStory(32L, "https://cdn.example.com/stories/b.jpg");

        mockMvc.perform(get("/stories/active")
                        .header("Authorization", bearer(31L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)));
    }

    @Test
    void viewingStory_increasesViewCount() throws Exception {
        createStory(41L, "https://cdn.example.com/stories/view.jpg");
        Long storyId = storyRepository.findAll().get(0).getStoryId();

        mockMvc.perform(put("/stories/{id}/view", storyId)
                        .header("Authorization", bearer(42L)))
                .andExpect(status().isOk());

        StoryEntity updatedStory = storyRepository.findById(storyId).orElseThrow();
        assertEquals(1, updatedStory.getViewsCount());
    }

    @Test
    void deleteStory_removesStory() throws Exception {
        createStory(51L, "https://cdn.example.com/stories/delete.jpg");
        Long storyId = storyRepository.findAll().get(0).getStoryId();

        mockMvc.perform(delete("/stories/{id}", storyId)
                        .header("Authorization", bearer(51L)))
                .andExpect(status().isOk());

        assertFalse(storyRepository.existsById(storyId));
    }

    @Test
    void expiryCheck_simulatedExpirationMarksStoryInactiveAndHidesFromActiveList() throws Exception {
        createStory(61L, "https://cdn.example.com/stories/expiry.jpg");
        StoryEntity story = storyRepository.findAll().get(0);
        story.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        story.setIsActive(true);
        storyRepository.save(story);

        storyService.expireOldStories();

        StoryEntity expired = storyRepository.findById(story.getStoryId()).orElseThrow();
        assertTrue(expired.getIsActive() != null && !expired.getIsActive());

        mockMvc.perform(get("/stories/active")
                        .header("Authorization", bearer(61L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)));
    }

    private void uploadMedia(Long uploaderId, String url, Long postId) throws Exception {
        mockMvc.perform(post("/media")
                        .header("Authorization", bearer(uploaderId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "url":"%s",
                                  "mediaType":"IMAGE",
                                  "sizeKb":256,
                                  "mimeType":"image/jpeg",
                                  "linkedPostId":%d
                                }
                                """.formatted(url, postId)))
                .andExpect(status().isCreated());
    }

    private void createStory(Long authorId, String mediaUrl) throws Exception {
        mockMvc.perform(post("/stories")
                        .header("Authorization", bearer(authorId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(storyJson(mediaUrl, "sample", "IMAGE")))
                .andExpect(status().isCreated());
    }

    private String storyJson(String mediaUrl, String caption, String mediaType) {
        return """
                {
                  "mediaUrl":"%s",
                  "caption":"%s",
                  "mediaType":"%s"
                }
                """.formatted(mediaUrl, caption, mediaType);
    }

    private String bearer(Long userId) {
        return "Bearer " + tokenFor(userId);
    }

    private String tokenFor(Long userId) {
        return Jwts.builder()
                .setSubject(userId.toString())
                .signWith(Keys.hmacShaKeyFor(JWT_SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }
}
