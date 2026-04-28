package com.connectsphere.auth.resource;

import com.connectsphere.auth.config.UserMapper;
import com.connectsphere.auth.dto.AuthDto.*;
import com.connectsphere.auth.entity.User;
import com.connectsphere.auth.security.JwtUtils;
import com.connectsphere.auth.service.AuthService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthResource.class)
@DisplayName("AuthResource — Integration Tests (MockMvc)")
class AuthResourceTest {

    @Autowired MockMvc     mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean AuthService authService;
    @MockBean UserMapper  userMapper;
    @MockBean JwtUtils    jwtUtils;

    // ── Helpers ───────────────────────────────────────────

    private UserResponse buildUserResponse() {
        UserResponse r = new UserResponse();
        r.setUserId(1);
        r.setUsername("alice");
        r.setEmail("alice@example.com");
        r.setRole("ROLE_USER");
        r.setIsActive(true);
        return r;
    }

    private AuthResponse buildAuthResponse() {
        return new AuthResponse("jwt.token.here", buildUserResponse());
    }

    // ── POST /register ────────────────────────────────────

    @Nested
    @DisplayName("POST /api/v1/auth/register")
    class RegisterEndpoint {

        @Test
        @DisplayName("201 on valid registration payload")
        void valid() throws Exception {
            RegisterRequest req = new RegisterRequest();
            req.setUsername("alice");
            req.setEmail("alice@example.com");
            req.setPassword("secret123");

            when(authService.register(any())).thenReturn(buildAuthResponse());

            mockMvc.perform(post("/api/v1/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req))
                            .with(csrf()))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.token").value("jwt.token.here"))
                    .andExpect(jsonPath("$.user.email").value("alice@example.com"));
        }

        @Test
        @DisplayName("400 on blank username")
        void blankUsername() throws Exception {
            RegisterRequest req = new RegisterRequest();
            req.setUsername("");
            req.setEmail("alice@example.com");
            req.setPassword("secret123");

            mockMvc.perform(post("/api/v1/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req))
                            .with(csrf()))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("400 on invalid email format")
        void invalidEmail() throws Exception {
            RegisterRequest req = new RegisterRequest();
            req.setUsername("alice");
            req.setEmail("not-an-email");
            req.setPassword("secret123");

            mockMvc.perform(post("/api/v1/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req))
                            .with(csrf()))
                    .andExpect(status().isBadRequest());
        }
    }

    // ── POST /login ───────────────────────────────────────

    @Nested
    @DisplayName("POST /api/v1/auth/login")
    class LoginEndpoint {

        @Test
        @DisplayName("200 with valid credentials")
        void valid() throws Exception {
            LoginRequest req = new LoginRequest();
            req.setEmail("alice@example.com");
            req.setPassword("secret123");

            when(authService.login(any())).thenReturn(buildAuthResponse());

            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req))
                            .with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.token").exists())
                    .andExpect(jsonPath("$.tokenType").value("Bearer"));
        }

        @Test
        @DisplayName("400 on blank fields")
        void blankFields() throws Exception {
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"\",\"password\":\"\"}")
                            .with(csrf()))
                    .andExpect(status().isBadRequest());
        }
    }

    // ── GET /profile ──────────────────────────────────────

    @Nested
    @DisplayName("GET /api/v1/auth/profile")
    class ProfileEndpoint {

        @Test
        @WithMockUser(username = "1", roles = "USER")
        @DisplayName("200 for authenticated user")
        void authenticated() throws Exception {
            User user = User.builder()
                    .userId(1).username("alice").email("alice@example.com")
                    .role("ROLE_USER").isActive(true).build();

            when(authService.getUserById(anyInt())).thenReturn(user);
            when(userMapper.toResponse(any())).thenReturn(buildUserResponse());

            mockMvc.perform(get("/api/v1/auth/profile"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.username").value("alice"));
        }
    }

    // ── GET /search ───────────────────────────────────────

    @Nested
    @DisplayName("GET /api/v1/auth/search")
    class SearchEndpoint {

        @Test
        @WithMockUser(roles = "USER")
        @DisplayName("200 with results")
        void withResults() throws Exception {
            User user = User.builder()
                    .userId(1).username("alice").email("alice@example.com")
                    .isActive(true).role("ROLE_USER").build();

            when(authService.searchUsers("alice")).thenReturn(List.of(user));
            when(userMapper.toResponse(any())).thenReturn(buildUserResponse());

            mockMvc.perform(get("/api/v1/auth/search").param("q", "alice"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.total").value(1))
                    .andExpect(jsonPath("$.users[0].username").value("alice"));
        }
    }

    // ── POST /logout ──────────────────────────────────────

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("POST /logout — 200 with message")
    void logout() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout")
                        .header("Authorization", "Bearer jwt.token.here")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Logged out successfully"));
    }
}
