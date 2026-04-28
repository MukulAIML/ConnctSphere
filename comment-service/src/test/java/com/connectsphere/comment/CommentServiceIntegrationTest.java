package com.connectsphere.comment;

import com.connectsphere.comment.entity.Comment;
import com.connectsphere.comment.repository.CommentRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:commentdb;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false",
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
class CommentServiceIntegrationTest {

    private static final String JWT_SECRET = "ConnectSphereSecretKey2026ConnectSphereSecretKey2026ConnectSphereSecretKey";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CommentRepository commentRepository;

    @MockBean
    private RestTemplate restTemplate;

    @BeforeEach
    void setup() {
        commentRepository.deleteAll();
        Mockito.reset(restTemplate);

        Mockito.when(restTemplate.exchange(
                        Mockito.contains("http://post-service:8081/posts/"),
                        Mockito.eq(HttpMethod.GET),
                        Mockito.isNull(),
                        Mockito.eq(Map.class)))
                .thenReturn(org.springframework.http.ResponseEntity.ok(Map.of("authorId", 999L)));
    }

    @Test
    void addComment_createsComment() throws Exception {
        mockMvc.perform(post("/comments")
                        .header("Authorization", bearer(11L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "postId": 1001,
                                  "content": "First comment"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.postId").value(1001))
                .andExpect(jsonPath("$.data.content").value("First comment"));

        assertEquals(1, commentRepository.countByPostIdAndIsDeletedFalse(1001L));
    }

    @Test
    void getCommentsOfPost_returnsComments() throws Exception {
        createComment(21L, 2001L, "Comment one");
        createComment(22L, 2001L, "Comment two");
        createComment(23L, 2002L, "Other post comment");

        mockMvc.perform(get("/comments/post/{postId}", 2001L)
                        .header("Authorization", bearer(21L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)));
    }

    @Test
    void replyToComment_createsNestedReply() throws Exception {
        createComment(31L, 3001L, "Parent comment");
        Long parentId = commentRepository.findAll().get(0).getCommentId();

        mockMvc.perform(post("/comments")
                        .header("Authorization", bearer(32L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "postId": 3001,
                                  "parentCommentId": %d,
                                  "content": "Reply comment"
                                }
                                """.formatted(parentId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.parentCommentId").value(parentId));

        mockMvc.perform(get("/comments/{id}/replies", parentId)
                        .header("Authorization", bearer(31L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].parentCommentId").value(parentId));
    }

    @Test
    void updateComment_updatesContent() throws Exception {
        createComment(41L, 4001L, "Old text");
        Long commentId = commentRepository.findAll().get(0).getCommentId();

        mockMvc.perform(put("/comments/{id}", commentId)
                        .header("Authorization", bearer(41L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "postId": 4001,
                                  "content": "Updated text"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").value("Updated text"));
    }

    @Test
    void deleteComment_softDeletesComment() throws Exception {
        createComment(51L, 5001L, "To delete");
        Long commentId = commentRepository.findAll().get(0).getCommentId();

        mockMvc.perform(delete("/comments/{id}", commentId)
                        .header("Authorization", bearer(51L)))
                .andExpect(status().isOk());

        Comment stored = commentRepository.findById(commentId).orElseThrow();
        assertTrue(stored.getIsDeleted());

        mockMvc.perform(get("/comments/{id}", commentId)
                        .header("Authorization", bearer(51L)))
                .andExpect(status().isNotFound());
    }

    @Test
    void likeUnlikeComment_updatesLikeCount() throws Exception {
        createComment(61L, 6001L, "Like target");
        Long commentId = commentRepository.findAll().get(0).getCommentId();

        mockMvc.perform(post("/comments/{id}/like", commentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.likesCount").value(1));

        mockMvc.perform(delete("/comments/{id}/like", commentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.likesCount").value(0));
    }

    @Test
    void integrationCheck_addCommentTriggersPostServiceCommentCountIncrement() throws Exception {
        mockMvc.perform(post("/comments")
                        .header("Authorization", bearer(71L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "postId": 7001,
                                  "content": "Integration comment"
                                }
                                """))
                .andExpect(status().isCreated());

        verify(restTemplate, atLeastOnce()).exchange(
                eq("http://post-service:8081/posts/7001/comment/increment"),
                eq(HttpMethod.PUT),
                isNull(),
                eq(Void.class)
        );

        assertFalse(commentRepository.findByPostIdAndParentCommentIdIsNullAndIsDeletedFalseOrderByCreatedAtDesc(7001L).isEmpty());
    }

    private void createComment(Long userId, Long postId, String content) throws Exception {
        mockMvc.perform(post("/comments")
                        .header("Authorization", bearer(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "postId": %d,
                                  "content": "%s"
                                }
                                """.formatted(postId, content)))
                .andExpect(status().isCreated());
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
