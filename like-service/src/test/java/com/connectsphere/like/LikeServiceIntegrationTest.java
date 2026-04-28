package com.connectsphere.like;

import com.connectsphere.like.entity.LikeEntity;
import com.connectsphere.like.entity.ReactionType;
import com.connectsphere.like.entity.TargetType;
import com.connectsphere.like.repository.LikeRepository;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:likedb;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false",
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
class LikeServiceIntegrationTest {

    private static final String JWT_SECRET = "ConnectSphereSecretKey2026ConnectSphereSecretKey2026ConnectSphereSecretKey";

    @Autowired
    private org.springframework.test.web.servlet.MockMvc mockMvc;

    @Autowired
    private LikeRepository likeRepository;

    @MockBean
    private RestTemplate restTemplate;

    @BeforeEach
    void setup() {
        likeRepository.deleteAll();
    }

    @Test
    void likePost_addsLike() throws Exception {
        like(1L, 1001L, "LIKE")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.targetId").value(1001))
                .andExpect(jsonPath("$.data.targetType").value("POST"))
                .andExpect(jsonPath("$.data.reactionType").value("LIKE"));

        assertEquals(1, likeRepository.countByTargetIdAndTargetType(1001L, TargetType.POST));
    }

    @Test
    void unlike_removesLike() throws Exception {
        like(1L, 1002L, "LIKE").andExpect(status().isCreated());

        mockMvc.perform(delete("/likes")
                        .queryParam("targetId", "1002")
                        .queryParam("targetType", "POST")
                        .header("Authorization", bearer(1L))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        assertEquals(0, likeRepository.countByTargetIdAndTargetType(1002L, TargetType.POST));
    }

    @Test
    void changeReaction_updatesReactionType() throws Exception {
        like(1L, 1003L, "LIKE").andExpect(status().isCreated());

        mockMvc.perform(put("/likes")
                        .queryParam("targetId", "1003")
                        .queryParam("targetType", "POST")
                        .queryParam("reactionType", "LOVE")
                        .header("Authorization", bearer(1L))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reactionType").value("LOVE"));

        LikeEntity updated = likeRepository
                .findByUserIdAndTargetIdAndTargetType(1L, 1003L, TargetType.POST)
                .orElseThrow();
        assertEquals(ReactionType.LOVE, updated.getReactionType());
    }

    @Test
    void getLikesCount_returnsCorrectCount() throws Exception {
        like(1L, 1004L, "LIKE").andExpect(status().isCreated());
        like(2L, 1004L, "LOVE").andExpect(status().isCreated());

        mockMvc.perform(get("/likes/count")
                        .queryParam("targetId", "1004")
                        .queryParam("targetType", "POST")
                        .header("Authorization", bearer(1L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(2));
    }

    @Test
    void reactionSummary_returnsReactionMap() throws Exception {
        like(1L, 1005L, "LIKE").andExpect(status().isCreated());
        like(2L, 1005L, "LOVE").andExpect(status().isCreated());
        like(3L, 1005L, "LOVE").andExpect(status().isCreated());

        mockMvc.perform(get("/likes/summary")
                        .queryParam("targetId", "1005")
                        .queryParam("targetType", "POST")
                        .header("Authorization", bearer(1L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.LIKE").value(1))
                .andExpect(jsonPath("$.data.LOVE").value(2));
    }

    @Test
    void sameUserCannotLikeSamePostTwice() throws Exception {
        like(9L, 1006L, "LIKE").andExpect(status().isCreated());
        like(9L, 1006L, "LOVE").andExpect(status().isCreated());

        assertEquals(1, likeRepository.countByTargetIdAndTargetType(1006L, TargetType.POST));

        LikeEntity existing = likeRepository
                .findByUserIdAndTargetIdAndTargetType(9L, 1006L, TargetType.POST)
                .orElseThrow();
        assertEquals(ReactionType.LOVE, existing.getReactionType());
    }

    private org.springframework.test.web.servlet.ResultActions like(Long userId, Long targetId, String reactionType) throws Exception {
        return mockMvc.perform(post("/likes")
                .queryParam("targetId", targetId.toString())
                .queryParam("targetType", "POST")
                .queryParam("reactionType", reactionType)
                .header("Authorization", bearer(userId))
                .contentType(MediaType.APPLICATION_JSON));
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
