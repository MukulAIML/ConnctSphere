package com.connectsphere.follow;

import com.connectsphere.follow.repository.FollowRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;

import static org.hamcrest.Matchers.hasItems;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:followdb;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false",
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
class FollowServiceIntegrationTest {

    private static final String JWT_SECRET = "ConnectSphereSecretKey2026ConnectSphereSecretKey2026ConnectSphereSecretKey";

    @Autowired
    private org.springframework.test.web.servlet.MockMvc mockMvc;

    @Autowired
    private FollowRepository followRepository;

    @MockBean
    private RestTemplate restTemplate;

    @BeforeEach
    void setup() {
        followRepository.deleteAll();
    }

    @Test
    void followUser_createsFollow() throws Exception {
        follow(1L, 2L)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.followerId").value(1))
                .andExpect(jsonPath("$.data.followeeId").value(2));

        assertTrue(followRepository.existsByFollowerIdAndFolloweeId(1L, 2L));
    }

    @Test
    void unfollow_removesFollow() throws Exception {
        follow(1L, 2L).andExpect(status().isCreated());

        mockMvc.perform(delete("/follows")
                        .queryParam("followeeId", "2")
                        .header("Authorization", bearer(1L)))
                .andExpect(status().isOk());

        assertFalse(followRepository.existsByFollowerIdAndFolloweeId(1L, 2L));
    }

    @Test
    void checkFollowing_returnsBoolean() throws Exception {
        follow(1L, 2L).andExpect(status().isCreated());

        mockMvc.perform(get("/follows/isFollowing")
                        .queryParam("followeeId", "2")
                        .header("Authorization", bearer(1L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(true));

        mockMvc.perform(get("/follows/isFollowing")
                        .queryParam("followeeId", "3")
                        .header("Authorization", bearer(1L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(false));
    }

    @Test
    void getFollowersAndFollowing_returnsExpectedLists() throws Exception {
        follow(1L, 2L).andExpect(status().isCreated());
        follow(3L, 2L).andExpect(status().isCreated());
        follow(2L, 4L).andExpect(status().isCreated());

        mockMvc.perform(get("/follows/followers/2")
                        .header("Authorization", bearer(1L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[*].followerId", hasItems(1, 3)));

        mockMvc.perform(get("/follows/following/2")
                        .header("Authorization", bearer(2L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].followeeId").value(4));
    }

    @Test
    void counts_returnCorrectFollowerAndFollowingCounts() throws Exception {
        follow(1L, 2L).andExpect(status().isCreated());
        follow(3L, 2L).andExpect(status().isCreated());
        follow(1L, 4L).andExpect(status().isCreated());

        mockMvc.perform(get("/follows/count/followers/2")
                        .header("Authorization", bearer(1L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(2));

        mockMvc.perform(get("/follows/count/following/1")
                        .header("Authorization", bearer(1L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(2));
    }

    @Test
    void mutualConnections_endpointWorks() throws Exception {
        follow(1L, 2L).andExpect(status().isCreated());
        follow(2L, 1L).andExpect(status().isCreated());
        follow(1L, 3L).andExpect(status().isCreated());
        follow(4L, 1L).andExpect(status().isCreated());

        mockMvc.perform(get("/follows/mutual")
                        .queryParam("userId", "1")
                        .header("Authorization", bearer(1L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0]").value(2));
    }

    @Test
    void suggestedUsers_recommendationLogicWorks() throws Exception {
        follow(1L, 2L).andExpect(status().isCreated());
        follow(1L, 4L).andExpect(status().isCreated());
        follow(2L, 3L).andExpect(status().isCreated());
        follow(2L, 4L).andExpect(status().isCreated());
        follow(4L, 5L).andExpect(status().isCreated());

        mockMvc.perform(get("/follows/suggestions")
                        .queryParam("userId", "1")
                        .header("Authorization", bearer(1L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data", hasItems(3, 5)));
    }

    private org.springframework.test.web.servlet.ResultActions follow(Long followerId, Long followeeId) throws Exception {
        return mockMvc.perform(post("/follows")
                .header("Authorization", bearer(followerId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"followeeId\":" + followeeId + "}"));
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
