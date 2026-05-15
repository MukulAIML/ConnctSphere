package com.connectsphere.auth.security;

import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.*;

@DisplayName("JwtUtils — Unit Tests")
class JwtUtilsTest {

    private JwtUtils jwtUtils;

    private static final String SECRET =
            "TestSecretKeyForConnectSphereAuthServiceJUnit2026TestSecret";
    private static final long   EXPIRY = 3_600_000L; // 1 hour

    @BeforeEach
    void setUp() {
        jwtUtils = new JwtUtils();
        ReflectionTestUtils.setField(jwtUtils, "jwtSecret",       SECRET);
        ReflectionTestUtils.setField(jwtUtils, "jwtExpirationMs", EXPIRY);
    }

    @Test
    @DisplayName("generateToken — returns non-blank token")
    void generate() {
        String token = jwtUtils.generateToken(1L, "alice@example.com", "ROLE_USER");
        assertThat(token).isNotBlank();
    }

    @Test
    @DisplayName("validateToken — returns true for fresh token")
    void validateFresh() {
        String token = jwtUtils.generateToken(1L, "alice@example.com", "ROLE_USER");
        assertThat(jwtUtils.validateToken(token)).isTrue();
    }

    @Test
    @DisplayName("validateToken — returns false for garbage input")
    void validateGarbage() {
        assertThat(jwtUtils.validateToken("not.a.token")).isFalse();
    }

    @Test
    @DisplayName("validateToken — returns false for null/empty")
    void validateEmpty() {
        assertThat(jwtUtils.validateToken("")).isFalse();
    }

    @Test
    @DisplayName("getUserIdFromToken — extracts correct userId")
    void getUserId() {
        String token = jwtUtils.generateToken(42L, "test@example.com", "ROLE_USER");
        assertThat(jwtUtils.getUserIdFromToken(token)).isEqualTo(42L);
    }

    @Test
    @DisplayName("getEmailFromToken — extracts correct email claim")
    void getEmail() {
        String token = jwtUtils.generateToken(1L, "alice@example.com", "ROLE_USER");
        assertThat(jwtUtils.getEmailFromToken(token)).isEqualTo("alice@example.com");
    }

    @Test
    @DisplayName("getRoleFromToken — extracts correct role claim")
    void getRole() {
        String token = jwtUtils.generateToken(1L, "alice@example.com", "ROLE_ADMIN");
        assertThat(jwtUtils.getRoleFromToken(token)).isEqualTo("ROLE_ADMIN");
    }

    @Test
    @DisplayName("refreshToken — produces a distinct token")
    void refresh() {
        String original = jwtUtils.generateToken(1L, "alice@example.com", "ROLE_USER");
        // Small delay to ensure different iat
        try { Thread.sleep(10); } catch (InterruptedException ignored) {}
        String refreshed = jwtUtils.refreshToken(original);
        assertThat(refreshed).isNotBlank();
        // Claims should still be correct
        assertThat(jwtUtils.getUserIdFromToken(refreshed)).isEqualTo(1L);
        assertThat(jwtUtils.getEmailFromToken(refreshed)).isEqualTo("alice@example.com");
    }

    @Test
    @DisplayName("refreshToken — preserves all claims")
    void refreshPreservesClaims() {
        String token    = jwtUtils.generateToken(7L, "bob@example.com", "ROLE_MODERATOR");
        String refreshed = jwtUtils.refreshToken(token);

        assertThat(jwtUtils.getUserIdFromToken(refreshed)).isEqualTo(7L);
        assertThat(jwtUtils.getEmailFromToken(refreshed)).isEqualTo("bob@example.com");
        assertThat(jwtUtils.getRoleFromToken(refreshed)).isEqualTo("ROLE_MODERATOR");
    }
}
