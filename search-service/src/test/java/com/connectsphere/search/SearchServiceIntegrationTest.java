package com.connectsphere.search;

import com.connectsphere.search.repository.HashtagRepository;
import com.connectsphere.search.repository.PostHashtagRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:searchdb;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false",
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
class SearchServiceIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private HashtagRepository hashtagRepository;

    @Autowired
    private PostHashtagRepository postHashtagRepository;

    @MockBean
    private RestTemplate restTemplate;

    @BeforeEach
    void setup() {
        postHashtagRepository.deleteAll();
        hashtagRepository.deleteAll();
        Mockito.reset(restTemplate);
    }

    @Test
    void createPostWithTag_indexesHashtag() throws Exception {
        indexPost(1001L, "New post with #Java and #Spring");

        mockMvc.perform(get("/hashtags/post/{postId}", 1001L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[*].tag", hasItems("java", "spring")));
    }

    @Test
    void searchPosts_returnsPostsByKeyword() throws Exception {
        Mockito.when(restTemplate.exchange(
                        Mockito.contains("http://post-service:8081/posts/search"),
                        Mockito.eq(HttpMethod.GET),
                        Mockito.isNull(),
                        Mockito.eq(List.class)))
                .thenReturn(ResponseEntity.ok(List.of(
                        Map.of("postId", 201L),
                        Map.of("postId", 202L)
                )));

        mockMvc.perform(get("/search/posts").queryParam("keyword", "cloud"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data", hasItems(201, 202)));
    }

    @Test
    void searchUsers_returnsUsers() throws Exception {
        Mockito.when(restTemplate.exchange(
                        Mockito.contains("http://auth-service:8080/auth/search"),
                        Mockito.eq(HttpMethod.GET),
                        Mockito.isNull(),
                        Mockito.eq(Map.class)))
                .thenReturn(ResponseEntity.ok(Map.of(
                        "data", List.of(
                                Map.of("userId", 301L),
                                Map.of("userId", 302L)
                        )
                )));

        mockMvc.perform(get("/search/users").queryParam("keyword", "mu"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data", hasItems(301, 302)));
    }

    @Test
    void getHashtagsForPost_returnsPostHashtags() throws Exception {
        indexPost(4001L, "Hashtags #alpha #beta #gamma");

        mockMvc.perform(get("/hashtags/post/{postId}", 4001L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(3)))
                .andExpect(jsonPath("$.data[*].tag", hasItems("alpha", "beta", "gamma")));
    }

    @Test
    void trendingHashtags_returnsTopTags() throws Exception {
        indexPost(5001L, "first #trend #other");
        indexPost(5002L, "second #trend");

        mockMvc.perform(get("/hashtags/trending"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].tag").value("trend"))
                .andExpect(jsonPath("$.data[0].postCount").value(2));
    }

    @Test
    void postsByHashtag_returnsPostIds() throws Exception {
        indexPost(6001L, "content #topic");
        indexPost(6002L, "more content #topic #misc");

        mockMvc.perform(get("/hashtags/{tag}", "topic"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data", hasItems(6001, 6002)));
    }

    @Test
    void updateAndDeletePost_updatesIndexCorrectly() throws Exception {
        indexPost(7001L, "old content #java #spring");
        indexPost(7001L, "updated content #java #ai");

        mockMvc.perform(get("/hashtags/post/{postId}", 7001L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[*].tag", hasItems("java", "ai")));

        mockMvc.perform(get("/hashtags/{tag}", "spring"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)));

        mockMvc.perform(delete("/search/remove/{postId}", 7001L))
                .andExpect(status().isOk());

        mockMvc.perform(get("/hashtags/{tag}", "java"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)));
    }

    private void indexPost(Long postId, String content) throws Exception {
        mockMvc.perform(post("/search/index")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "postId": %d,
                                  "content": "%s"
                                }
                                """.formatted(postId, content)))
                .andExpect(status().isCreated());
    }
}
