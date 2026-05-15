package com.connectsphere.search;

import com.connectsphere.search.entity.HashtagEntity;
import com.connectsphere.search.entity.PostHashtagEntity;
import com.connectsphere.search.repository.HashtagElasticsearchRepository;
import com.connectsphere.search.repository.HashtagRepository;
import com.connectsphere.search.repository.PostHashtagRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for SearchService.
 *
 * Elasticsearch and RabbitMQ are mocked via @MockBean so this test runs
 * without external infrastructure dependencies.
 */
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
                "spring.cloud.discovery.enabled=false",
                // Disable auto-index creation so ES ops can be fully mocked
                "spring.elasticsearch.uris=http://localhost:9200"
        }
)
@AutoConfigureMockMvc
class SearchServiceIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private HashtagElasticsearchRepository elasticsearchRepository;

    @MockBean
    private ElasticsearchOperations elasticsearchOperations;

    @Autowired
    private PostHashtagRepository postHashtagRepository;

    @Autowired
    private HashtagRepository hashtagRepository;

    @MockBean
    private RestTemplate restTemplate;

    @MockBean
    private RabbitTemplate rabbitTemplate;

    @BeforeEach
    void setup() {
        postHashtagRepository.deleteAll();
        hashtagRepository.deleteAll();
        Mockito.reset(restTemplate, elasticsearchRepository, rabbitTemplate);
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
    void getTrendingHashtags_returnsTopTags() throws Exception {
        hashtagRepository.save(HashtagEntity.builder()
                .tag("trend")
                .postCount(5)
                .lastUsedAt(LocalDateTime.now())
                .build());

        mockMvc.perform(get("/hashtags/trending"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].tag").value("trend"))
                .andExpect(jsonPath("$.data[0].postCount").value(5));
    }

    @Test
    void searchHashtags_returnsMatchingTags() throws Exception {
        hashtagRepository.save(HashtagEntity.builder()
                .tag("java")
                .postCount(3)
                .lastUsedAt(LocalDateTime.now())
                .build());
        hashtagRepository.save(HashtagEntity.builder()
                .tag("springboot")
                .postCount(2)
                .lastUsedAt(LocalDateTime.now())
                .build());

        mockMvc.perform(get("/hashtags/search").queryParam("keyword", "jav"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].tag").value("java"));
    }

    @Test
    void indexPost_callsElasticsearch() throws Exception {
        Mockito.when(elasticsearchRepository.save(Mockito.any()))
                .thenAnswer(inv -> inv.getArgument(0));

        mockMvc.perform(post("/search/index")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "postId": 1001,
                                  "content": "Test post with #java and #spring"
                                }
                                """))
                .andExpect(status().isCreated());

        Mockito.verify(elasticsearchRepository, Mockito.atLeastOnce()).save(Mockito.any());
    }

    @Test
    void hashtagsUpdateAutomaticallyOnReindex() throws Exception {
        Mockito.when(elasticsearchRepository.save(Mockito.any()))
                .thenAnswer(inv -> inv.getArgument(0));

        mockMvc.perform(post("/search/index")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "postId": 5001,
                                  "content": "First #java #spring"
                                }
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/search/index")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "postId": 5001,
                                  "content": "Updated #java #kafka"
                                }
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/hashtags/post/{postId}", 5001L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[*].tag", hasItems("java", "kafka")));

        mockMvc.perform(get("/hashtags/count/{tag}", "spring"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(0));

        mockMvc.perform(get("/hashtags/{tag}", "kafka"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasItems(5001)));
    }

    @Test
    void emptyContentRemovesHashtagMappingsForPost() throws Exception {
        Mockito.when(elasticsearchRepository.save(Mockito.any()))
                .thenAnswer(inv -> inv.getArgument(0));

        mockMvc.perform(post("/search/index")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "postId": 7001,
                                  "content": "Content #cleanup"
                                }
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/search/index")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "postId": 7001,
                                  "content": ""
                                }
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/hashtags/post/{postId}", 7001L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)));

        List<PostHashtagEntity> mappings = postHashtagRepository.findByPostId(7001L);
        org.junit.jupiter.api.Assertions.assertEquals(0, mappings.size());
    }
}
