package com.connectsphere.auth.service;

import com.connectsphere.auth.config.UserMapper;
import com.connectsphere.auth.dto.AuthDto.*;
import com.connectsphere.auth.entity.User;
import com.connectsphere.auth.repository.UserRepository;
import com.connectsphere.auth.security.JwtUtils;
import com.connectsphere.auth.service.impl.AuthServiceImpl;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthServiceImpl — Unit Tests")
class AuthServiceImplTest {

    @Mock UserRepository  userRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock JwtUtils        jwtUtils;
    @Mock UserMapper      userMapper;
    @Mock RedisSessionService redisSessionService;

    @InjectMocks AuthServiceImpl authService;

    // ── Fixtures ──────────────────────────────────────────

    private User sampleUser() {
        return User.builder()
                .userId(1L)
                .username("alice")
                .email("alice@example.com")
                .passwordHash("$2a$12$hashedpw")
                .role("ROLE_USER")
                .provider("LOCAL")
                .isActive(true)
                .build();
    }

    private RegisterRequest registerRequest() {
        RegisterRequest r = new RegisterRequest();
        r.setUsername("alice");
        r.setEmail("alice@example.com");
        r.setPassword("secret123");
        r.setFullName("Alice Smith");
        return r;
    }

    private UserResponse sampleUserResponse(User u) {
        UserResponse r = new UserResponse();
        r.setUserId(u.getUserId());
        r.setUsername(u.getUsername());
        r.setEmail(u.getEmail());
        return r;
    }

    // ── register ──────────────────────────────────────────

    @Nested
    @DisplayName("register()")
    class Register {

        @Test
        @DisplayName("should register a new user and return JWT")
        void success() {
            User user = sampleUser();
            when(userRepository.existsByEmail(anyString())).thenReturn(false);
            when(userRepository.existsByUsername(anyString())).thenReturn(false);
            when(passwordEncoder.encode(anyString())).thenReturn("$2a$12$hashedpw");
            when(userRepository.save(any(User.class))).thenReturn(user);
            when(jwtUtils.generateAccessToken(anyLong(), anyString(), anyString())).thenReturn("jwt.token.here");
            when(jwtUtils.generateRefreshToken(anyLong(), anyString(), anyString())).thenReturn("refresh.token.here");
            when(userMapper.toResponse(any())).thenReturn(sampleUserResponse(user));

            AuthResponse resp = authService.register(registerRequest());

            assertThat(resp.getToken()).isEqualTo("jwt.token.here");
            assertThat(resp.getRefreshToken()).isEqualTo("refresh.token.here");
            assertThat(resp.getUser().getEmail()).isEqualTo("alice@example.com");
            verify(userRepository).save(any(User.class));
            verify(redisSessionService).cacheSession(1L, "jwt.token.here");
        }

        @Test
        @DisplayName("should throw when email already exists")
        void duplicateEmail() {
            when(userRepository.existsByEmail("alice@example.com")).thenReturn(true);

            assertThatThrownBy(() -> authService.register(registerRequest()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Email already in use");
        }

        @Test
        @DisplayName("should throw when username already taken")
        void duplicateUsername() {
            when(userRepository.existsByEmail(anyString())).thenReturn(false);
            when(userRepository.existsByUsername("alice")).thenReturn(true);

            assertThatThrownBy(() -> authService.register(registerRequest()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Username already taken");
        }
    }

    // ── login ─────────────────────────────────────────────

    @Nested
    @DisplayName("login()")
    class Login {

        @Test
        @DisplayName("should return JWT on valid credentials")
        void success() {
            User user = sampleUser();
            LoginRequest req = new LoginRequest();
            req.setEmail("alice@example.com");
            req.setPassword("secret123");

            when(userRepository.findByEmail("alice@example.com"))
                    .thenReturn(Optional.of(user));
            when(passwordEncoder.matches("secret123", user.getPasswordHash()))
                    .thenReturn(true);
            when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
            when(jwtUtils.generateAccessToken(anyLong(), anyString(), anyString()))
                    .thenReturn("jwt.token.here");
            when(jwtUtils.generateRefreshToken(anyLong(), anyString(), anyString()))
                    .thenReturn("refresh.token.here");
            when(userMapper.toResponse(any())).thenReturn(sampleUserResponse(user));

            AuthResponse resp = authService.login(req);

            assertThat(resp.getToken()).isEqualTo("jwt.token.here");
            assertThat(resp.getRefreshToken()).isEqualTo("refresh.token.here");
            verify(redisSessionService).cacheSession(1L, "jwt.token.here");
        }

        @Test
        @DisplayName("should throw on wrong password")
        void wrongPassword() {
            User user = sampleUser();
            LoginRequest req = new LoginRequest();
            req.setEmail("alice@example.com");
            req.setPassword("wrong");

            when(userRepository.findByEmail("alice@example.com"))
                    .thenReturn(Optional.of(user));
            when(passwordEncoder.matches("wrong", user.getPasswordHash()))
                    .thenReturn(false);

            assertThatThrownBy(() -> authService.login(req))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Invalid email or password");
        }

        @Test
        @DisplayName("should throw when user not found")
        void userNotFound() {
            LoginRequest req = new LoginRequest();
            req.setEmail("nobody@example.com");
            req.setPassword("pw");

            when(userRepository.findByEmail("nobody@example.com"))
                    .thenReturn(Optional.empty());
            when(userRepository.findByUsername("nobody@example.com"))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> authService.login(req))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("should throw when account is deactivated")
        void deactivatedAccount() {
            User user = sampleUser();
            user.setIsActive(false);
            LoginRequest req = new LoginRequest();
            req.setEmail("alice@example.com");
            req.setPassword("secret123");

            when(userRepository.findByEmail("alice@example.com"))
                    .thenReturn(Optional.of(user));

            assertThatThrownBy(() -> authService.login(req))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("deactivated");
        }
    }

    // ── validateToken / refreshToken ──────────────────────

    @Nested
    @DisplayName("token operations")
    class TokenOps {

        @Test
        @DisplayName("validateToken — delegates to JwtUtils")
        void validate() {
            User user = sampleUser();
            when(jwtUtils.validateToken("good.token")).thenReturn(true);
            when(redisSessionService.isTokenBlocked("good.token")).thenReturn(false);
            when(jwtUtils.getUserIdFromToken("good.token")).thenReturn(1L);
            when(userRepository.findByUserId(1L)).thenReturn(Optional.of(user));
            assertThat(authService.validateToken("good.token")).isTrue();
        }

        @Test
        @DisplayName("refreshToken — returns new token for valid input")
        void refresh() {
            User user = sampleUser();
            when(jwtUtils.validateToken("old.token")).thenReturn(true);
            when(redisSessionService.isTokenBlocked("old.token")).thenReturn(false);
            when(jwtUtils.getUserIdFromToken("old.token")).thenReturn(1L);
            when(userRepository.findByUserId(1L)).thenReturn(Optional.of(user));
            when(jwtUtils.refreshToken("old.token")).thenReturn("new.token");

            assertThat(authService.refreshToken("old.token")).isEqualTo("new.token");
            verify(redisSessionService).cacheSession(1L, "new.token");
        }

        @Test
        @DisplayName("refreshToken — throws for expired token")
        void refreshExpired() {
            when(jwtUtils.validateToken("expired.token")).thenReturn(false);

            assertThatThrownBy(() -> authService.refreshToken("expired.token"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("invalid or expired");
        }
    }

    // ── updateProfile ─────────────────────────────────────

    @Nested
    @DisplayName("updateProfile()")
    class UpdateProfile {

        @Test
        @DisplayName("should update bio and fullName")
        void success() {
            User user = sampleUser();
            UpdateProfileRequest req = new UpdateProfileRequest();
            req.setFullName("Alice Updated");
            req.setBio("New bio text");

            when(userRepository.findByUserId(1L)).thenReturn(Optional.of(user));
            when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            User updated = authService.updateProfile(1L, req);

            assertThat(updated.getFullName()).isEqualTo("Alice Updated");
            assertThat(updated.getBio()).isEqualTo("New bio text");
        }

        @Test
        @DisplayName("should update email when unique")
        void updateEmail() {
            User user = sampleUser();
            UpdateProfileRequest req = new UpdateProfileRequest();
            req.setEmail("newalice@example.com");

            when(userRepository.findByUserId(1L)).thenReturn(Optional.of(user));
            when(userRepository.existsByEmail("newalice@example.com")).thenReturn(false);
            when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            User updated = authService.updateProfile(1L, req);
            assertThat(updated.getEmail()).isEqualTo("newalice@example.com");
        }

        @Test
        @DisplayName("should throw if new username is already taken")
        void usernameTaken() {
            User user = sampleUser();
            UpdateProfileRequest req = new UpdateProfileRequest();
            req.setUsername("bob");

            when(userRepository.findByUserId(1L)).thenReturn(Optional.of(user));
            when(userRepository.existsByUsername("bob")).thenReturn(true);

            assertThatThrownBy(() -> authService.updateProfile(1L, req))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Username already taken");
        }
    }

    // ── changePassword ────────────────────────────────────

    @Nested
    @DisplayName("changePassword()")
    class ChangePassword {

        @Test
        @DisplayName("should change password when current is correct")
        void success() {
            User user = sampleUser();
            ChangePasswordRequest req = new ChangePasswordRequest();
            req.setCurrentPassword("secret123");
            req.setNewPassword("newSecret456");

            when(userRepository.findByUserId(1L)).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("secret123", user.getPasswordHash())).thenReturn(true);
            when(passwordEncoder.encode("newSecret456")).thenReturn("$2a$12$newhash");
            when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            assertThatCode(() -> authService.changePassword(1L, req))
                    .doesNotThrowAnyException();
            assertThat(user.getPasswordHash()).isEqualTo("$2a$12$newhash");
            verify(redisSessionService).evictSession(1L);
        }

        @Test
        @DisplayName("should throw when current password is wrong")
        void wrongCurrent() {
            User user = sampleUser();
            ChangePasswordRequest req = new ChangePasswordRequest();
            req.setCurrentPassword("wrong");
            req.setNewPassword("newSecret456");

            when(userRepository.findByUserId(1L)).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("wrong", user.getPasswordHash())).thenReturn(false);

            assertThatThrownBy(() -> authService.changePassword(1L, req))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Current password is incorrect");
        }
    }

    // ── deactivateAccount ─────────────────────────────────

    @Test
    @DisplayName("deactivateAccount() — sets isActive = false")
    void deactivate() {
        User user = sampleUser();
        when(userRepository.findByUserId(1L)).thenReturn(Optional.of(user));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        authService.deactivateAccount(1L);

        assertThat(user.getIsActive()).isFalse();
        verify(userRepository).save(user);
        verify(redisSessionService).evictSession(1L);
    }

    // ── searchUsers ───────────────────────────────────────

    @Test
    @DisplayName("searchUsers() — returns matching users")
    void search() {
        User alice = sampleUser();
        when(userRepository.searchByUsername("alice")).thenReturn(List.of(alice));

        List<User> results = authService.searchUsers("alice");

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getUsername()).isEqualTo("alice");
    }

    // ── handleOAuthLogin ──────────────────────────────────

    @Nested
    @DisplayName("handleOAuthLogin()")
    class OAuth {

        @Test
        @DisplayName("should create a new user for first-time OAuth login")
        void newUser() {
            when(userRepository.findByProviderAndProviderId("GOOGLE", "google-sub-123"))
                    .thenReturn(Optional.empty());
            when(userRepository.findByEmail("oauth@gmail.com"))
                    .thenReturn(Optional.empty());
            when(userRepository.existsByUsername(anyString())).thenReturn(false);
            when(userRepository.save(any(User.class))).thenAnswer(inv -> {
                User u = inv.getArgument(0);
                u = User.builder()
                        .userId(99L)
                        .username(u.getUsername())
                        .email(u.getEmail())
                        .provider("GOOGLE")
                        .isActive(true)
                        .role("ROLE_USER")
                        .build();
                return u;
            });
            when(jwtUtils.generateAccessToken(anyLong(), anyString(), anyString()))
                    .thenReturn("oauth.jwt.token");
            when(jwtUtils.generateRefreshToken(anyLong(), anyString(), anyString()))
                    .thenReturn("oauth.refresh.token");
            when(userMapper.toResponse(any())).thenAnswer(inv -> {
                User u = inv.getArgument(0);
                UserResponse r = new UserResponse();
                r.setUserId(u.getUserId());
                r.setEmail(u.getEmail());
                return r;
            });

            AuthResponse resp = authService.handleOAuthLogin(
                    "GOOGLE", "google-sub-123", "oauth@gmail.com", "OAuth User", null);

            assertThat(resp.getToken()).isEqualTo("oauth.jwt.token");
            assertThat(resp.getRefreshToken()).isEqualTo("oauth.refresh.token");
            verify(userRepository, times(2)).save(any(User.class));
        }

        @Test
        @DisplayName("should reuse existing user on repeat OAuth login")
        void existingUser() {
            User user = sampleUser();
            user.setProvider("GOOGLE");
            user.setProviderId("google-sub-123");

            when(userRepository.findByProviderAndProviderId("GOOGLE", "google-sub-123"))
                    .thenReturn(Optional.of(user));
            when(jwtUtils.generateAccessToken(anyLong(), anyString(), anyString()))
                    .thenReturn("reused.jwt");
            when(jwtUtils.generateRefreshToken(anyLong(), anyString(), anyString()))
                    .thenReturn("reused.refresh.jwt");
            when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
            when(userMapper.toResponse(any())).thenReturn(sampleUserResponse(user));

            AuthResponse resp = authService.handleOAuthLogin(
                    "GOOGLE", "google-sub-123", "alice@example.com", "Alice", null);

            assertThat(resp.getToken()).isEqualTo("reused.jwt");
            assertThat(resp.getRefreshToken()).isEqualTo("reused.refresh.jwt");
            verify(userRepository, times(1)).save(any(User.class));
        }
    }
}
