package com.connectsphere.notification;

import com.connectsphere.notification.repository.NotificationRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;

import static org.hamcrest.Matchers.hasItems;
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
                "spring.datasource.url=jdbc:h2:mem:notificationdb;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false",
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
class NotificationServiceIntegrationTest {

    private static final String JWT_SECRET = "ConnectSphereSecretKey2026ConnectSphereSecretKey2026ConnectSphereSecretKey";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private NotificationRepository notificationRepository;

    @BeforeEach
    void setup() {
        notificationRepository.deleteAll();
    }

    @Test
    void createNotification_manualTest() throws Exception {
        mockMvc.perform(post("/notifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(notificationJson(101L, 11L, "LIKE", "User 11 liked your post", 9001L, "POST")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.recipientId").value(101))
                .andExpect(jsonPath("$.data.type").value("LIKE"))
                .andExpect(jsonPath("$.data.targetType").value("POST"));

        assertEquals(1, notificationRepository.findByRecipientIdOrderByCreatedAtDesc(101L).size());
    }

    @Test
    void autoTriggerLikeComment_simulatedPayloadsCreateNotifications() throws Exception {
        mockMvc.perform(post("/notifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(notificationJson(201L, 21L, "LIKE", "like trigger", 5001L, "POST")))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/notifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(notificationJson(201L, 22L, "COMMENT", "comment trigger", 5001L, "POST")))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/notifications/{userId}", 201L)
                        .header("Authorization", bearer(201L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[*].type", hasItems("LIKE", "COMMENT")));
    }

    @Test
    void getNotifications_returnsRecipientNotifications() throws Exception {
        mockMvc.perform(post("/notifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(notificationJson(301L, 31L, "FOLLOW", "new follower", 31L, "USER")))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/notifications/{userId}", 301L)
                        .header("Authorization", bearer(301L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].recipientId").value(301));
    }

    @Test
    void unreadCount_returnsCorrectCount() throws Exception {
        mockMvc.perform(post("/notifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(notificationJson(401L, 41L, "LIKE", "n1", 7001L, "POST")))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/notifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(notificationJson(401L, 42L, "COMMENT", "n2", 7001L, "POST")))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/notifications/unreadCount")
                        .header("Authorization", bearer(401L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(2));
    }

    @Test
    void markAsRead_updatesNotificationStatus() throws Exception {
        mockMvc.perform(post("/notifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(notificationJson(501L, 51L, "LIKE", "to read", 8001L, "POST")))
                .andExpect(status().isCreated());

        Long notificationId = notificationRepository.findByRecipientIdOrderByCreatedAtDesc(501L).get(0).getNotificationId();

        mockMvc.perform(put("/notifications/read/{id}", notificationId)
                        .header("Authorization", bearer(501L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isRead").value(true));

        assertTrue(notificationRepository.findById(notificationId).orElseThrow().getIsRead());
    }

    @Test
    void markAllRead_marksAllUnreadNotifications() throws Exception {
        mockMvc.perform(post("/notifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(notificationJson(601L, 61L, "LIKE", "m1", 9001L, "POST")))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/notifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(notificationJson(601L, 62L, "COMMENT", "m2", 9002L, "COMMENT")))
                .andExpect(status().isCreated());

        mockMvc.perform(put("/notifications/readAll")
                        .header("Authorization", bearer(601L)))
                .andExpect(status().isOk());

        assertEquals(0, notificationRepository.countByRecipientIdAndIsReadFalse(601L));
    }

    @Test
    void deleteNotification_removesNotification() throws Exception {
        mockMvc.perform(post("/notifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(notificationJson(701L, 71L, "LIKE", "delete me", 9100L, "POST")))
                .andExpect(status().isCreated());

        Long notificationId = notificationRepository.findByRecipientIdOrderByCreatedAtDesc(701L).get(0).getNotificationId();

        mockMvc.perform(delete("/notifications/{id}", notificationId)
                        .header("Authorization", bearer(701L)))
                .andExpect(status().isOk());

        assertFalse(notificationRepository.findById(notificationId).isPresent());
    }

    @Test
    void bulkNotification_createsNotificationsForAllRecipients() throws Exception {
        mockMvc.perform(post("/notifications/bulk")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "recipientIds":[801,802,803],
                                  "actorId":80,
                                  "type":"MENTION",
                                  "message":"Platform announcement",
                                  "targetId":9999,
                                  "targetType":"POST"
                                }
                                """))
                .andExpect(status().isCreated());

        assertEquals(3, notificationRepository.findAll().size());
        assertEquals(1, notificationRepository.findByRecipientIdOrderByCreatedAtDesc(801L).size());
        assertEquals(1, notificationRepository.findByRecipientIdOrderByCreatedAtDesc(802L).size());
        assertEquals(1, notificationRepository.findByRecipientIdOrderByCreatedAtDesc(803L).size());
    }

    private String notificationJson(Long recipientId, Long actorId, String type, String message, Long targetId, String targetType) {
        return """
                {
                  "recipientId": %d,
                  "actorId": %d,
                  "type": "%s",
                  "message": "%s",
                  "targetId": %d,
                  "targetType": "%s"
                }
                """.formatted(recipientId, actorId, type, message, targetId, targetType);
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
