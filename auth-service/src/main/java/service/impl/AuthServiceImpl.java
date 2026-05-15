package com.connectsphere.auth.service.impl;

import com.connectsphere.auth.config.UserMapper;
import com.connectsphere.auth.dto.AuthDto.*;
import com.connectsphere.auth.entity.User;
import com.connectsphere.auth.repository.UserRepository;
import com.connectsphere.auth.security.JwtUtils;
import com.connectsphere.auth.service.AuthService;
import com.connectsphere.auth.service.RedisSessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

/**
 * AuthServiceImpl — implements the complete identity & user management
 * contract defined by {@link AuthService}.
 *
 * Responsibilities:
 *  - Local registration (bcrypt password hashing)
 *  - Email/password login with JWT issuance
 *  - OAuth2 find-or-create flow (Google / GitHub)
 *  - JWT validation, refresh, and logout
 *  - Profile update, password change, account deactivation
 *  - Full-text user search
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class AuthServiceImpl implements AuthService {

    private final UserRepository   userRepository;
    private final PasswordEncoder  passwordEncoder;
    private final JwtUtils         jwtUtils;
    private final UserMapper       userMapper;
    private final RedisSessionService redisSessionService;

    // ── Register ──────────────────────────────────────────

    @Override
    public AuthResponse register(RegisterRequest request) {
        String normalizedEmail = normalizeEmail(request.getEmail());
        String normalizedUsername = normalizeUsername(request.getUsername());

        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new IllegalArgumentException(
                "Email already in use: " + normalizedEmail);
        }
        if (userRepository.existsByUsername(normalizedUsername)) {
            throw new IllegalArgumentException(
                "Username already taken: " + normalizedUsername);
        }

        User user = User.builder()
                .username(normalizedUsername)
                .email(normalizedEmail)
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .fullName(request.getFullName())
                .provider("LOCAL")
                .lastLoginAt(LocalDateTime.now())
                .build();

        user = userRepository.save(user);
        log.info("New user registered: {}", user.getEmail());

        String token = jwtUtils.generateAccessToken(
                user.getUserId(), user.getEmail(), user.getRole());
        String refreshToken = jwtUtils.generateRefreshToken(
                user.getUserId(), user.getEmail(), user.getRole());
        redisSessionService.cacheSession(user.getUserId(), token);
        return new AuthResponse(token, refreshToken, userMapper.toResponse(user));
    }

    // ── Login ─────────────────────────────────────────────

    @Override
    public AuthResponse login(LoginRequest request) {
        // Find by email or username
        String principal = request.getEmail() == null ? null : request.getEmail().trim();
        String normalizedEmail = normalizeEmail(principal);
        User user = userRepository.findByEmail(normalizedEmail)
                .or(() -> userRepository.findByUsername(principal))
                .orElseThrow(() -> new IllegalArgumentException(
                        "Invalid email/username or password"));

        if (!user.getIsActive()) {
            throw new IllegalStateException("Account is deactivated");
        }
        if (Boolean.TRUE.equals(user.getIsSuspended())) {
            throw new IllegalStateException("Account is suspended");
        }
        if (user.getPasswordHash() == null ||
                !passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException("Invalid email or password");
        }

        user.setLastLoginAt(LocalDateTime.now());
        user = userRepository.save(user);

        String token = jwtUtils.generateAccessToken(
                user.getUserId(), user.getEmail(), user.getRole());
        String refreshToken = jwtUtils.generateRefreshToken(
                user.getUserId(), user.getEmail(), user.getRole());
        redisSessionService.cacheSession(user.getUserId(), token);
        log.debug("User logged in: {}", user.getEmail());
        return new AuthResponse(token, refreshToken, userMapper.toResponse(user));
    }

    // ── Logout (stateless — client discards token) ────────

    @Override
    public void logout(String token) {
        if (token != null && !token.isBlank() && jwtUtils.validateToken(token)) {
            // Block the token in Redis for its remaining lifetime
            long remainingTtl = jwtUtils.getRemainingTtlSeconds(token);
            redisSessionService.blockToken(token, remainingTtl);
            // Evict the session cache for this user
            Long userId = jwtUtils.getUserIdFromToken(token);
            redisSessionService.evictSession(userId);
            log.debug("User logged out — token blocklisted, session evicted for userId={}", userId);
        } else {
            log.debug("Logout called with null/invalid token — no-op");
        }
    }

    // ── Token Validation & Refresh ────────────────────────

    @Override
    @Transactional(readOnly = true)
    public boolean validateToken(String token) {
        if (!StringUtils.hasText(token) || !jwtUtils.validateToken(token)) {
            return false;
        }
        if (redisSessionService.isTokenBlocked(token)) {
            return false;
        }
        Long userId = jwtUtils.getUserIdFromToken(token);
        return userRepository.findByUserId(userId)
                .map(user -> Boolean.TRUE.equals(user.getIsActive()) &&
                             !Boolean.TRUE.equals(user.getIsSuspended()))
                .orElse(false);
    }

    @Override
    @Transactional(readOnly = true)
    public String refreshToken(String token) {
        if (!StringUtils.hasText(token) || !jwtUtils.validateToken(token)) {
            throw new IllegalArgumentException("Token is invalid or expired");
        }
        if (redisSessionService.isTokenBlocked(token)) {
            throw new IllegalArgumentException("Token is invalid or expired");
        }
        Long userId = jwtUtils.getUserIdFromToken(token);
        User user = getUserById(userId);
        if (!user.getIsActive()) {
            throw new IllegalStateException("Account is deactivated");
        }
        if (Boolean.TRUE.equals(user.getIsSuspended())) {
            throw new IllegalStateException("Account is suspended");
        }
        String refreshedToken = jwtUtils.refreshToken(token);
        redisSessionService.cacheSession(user.getUserId(), refreshedToken);
        return refreshedToken;
    }

    // ── User Lookups ──────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public User getUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException(
                        "User not found: " + email));
    }

    @Override
    @Transactional(readOnly = true)
    public User getUserById(Long userId) {
        return userRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "User not found: id=" + userId));
    }

    // ── Profile Management ────────────────────────────────

    @Override
    public User updateProfile(Long userId, UpdateProfileRequest request) {
        User user = getUserById(userId);

        String requestedUsername = normalizeUsername(request.getUsername());
        if (StringUtils.hasText(requestedUsername) &&
                !requestedUsername.equals(user.getUsername())) {
            if (userRepository.existsByUsername(requestedUsername)) {
                throw new IllegalArgumentException(
                    "Username already taken: " + requestedUsername);
            }
            user.setUsername(requestedUsername);
        }

        String requestedEmail = normalizeEmail(request.getEmail());
        if (StringUtils.hasText(requestedEmail) &&
                !requestedEmail.equals(user.getEmail())) {
            if (userRepository.existsByEmail(requestedEmail)) {
                throw new IllegalArgumentException(
                    "Email already in use: " + requestedEmail);
            }
            user.setEmail(requestedEmail);
        }

        if (request.getFullName()    != null) user.setFullName(request.getFullName());
        if (request.getBio()         != null) user.setBio(request.getBio());
        if (request.getProfilePicUrl() != null) user.setProfilePicUrl(request.getProfilePicUrl());

        return userRepository.save(user);
    }

    @Override
    public void changePassword(Long userId, ChangePasswordRequest request) {
        User user = getUserById(userId);

        if (!"LOCAL".equalsIgnoreCase(user.getProvider())) {
            throw new IllegalStateException("Password change is only available for LOCAL accounts");
        }
        if (user.getPasswordHash() == null ||
                !passwordEncoder.matches(request.getCurrentPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException("Current password is incorrect");
        }
        if (request.getCurrentPassword().equals(request.getNewPassword())) {
            throw new IllegalArgumentException("New password must be different from the current password");
        }

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
        redisSessionService.evictSession(userId);
        log.info("Password changed for userId={}", userId);
    }

    // ── Account Deactivation ──────────────────────────────

    @Override
    public void deactivateAccount(Long userId) {
        User user = getUserById(userId);
        user.setIsActive(false);
        userRepository.save(user);
        redisSessionService.evictSession(userId);
        log.info("Account deactivated: userId={}", userId);
    }

    // ── Search ────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public List<User> searchUsers(String query) {
        return userRepository.searchByUsername(query);
    }

    // ── OAuth2 Find-or-Create ─────────────────────────────

    @Override
    public AuthResponse handleOAuthLogin(String provider, String providerId,
                                         String email, String name, String avatarUrl) {
        String normalizedProvider = StringUtils.hasText(provider) ? provider.trim().toUpperCase(Locale.ROOT) : "OAUTH2";
        String normalizedProviderId = StringUtils.hasText(providerId) ? providerId.trim() : null;
        if (!StringUtils.hasText(normalizedProviderId)) {
            throw new IllegalArgumentException("OAuth providerId is required");
        }

        String resolvedEmail = normalizeEmail(email);
        if (!StringUtils.hasText(resolvedEmail)) {
            resolvedEmail = normalizedProvider.toLowerCase(Locale.ROOT) + "_" +
                    normalizedProviderId + "@oauth.connectsphere.local";
        }

        // 1. Try to find by provider + providerId
        User user = userRepository.findByProviderAndProviderId(normalizedProvider, normalizedProviderId)
                .orElse(null);

        if (user == null) {
            // 2. Try to find by email (link OAuth to existing local account)
            user = userRepository.findByEmail(resolvedEmail).orElse(null);

            if (user == null) {
                // 3. Create brand-new OAuth user
                String baseUsername = resolveBaseUsername(resolvedEmail, name, normalizedProvider, normalizedProviderId);
                String username     = resolveUniqueUsername(baseUsername);

                user = User.builder()
                        .username(username)
                        .email(resolvedEmail)
                        .fullName(name)
                        .profilePicUrl(avatarUrl)
                        .provider(normalizedProvider)
                        .providerId(normalizedProviderId)
                        .build();
            } else {
                // Link OAuth credentials to existing account
                user.setProvider(normalizedProvider);
                user.setProviderId(normalizedProviderId);
                if (user.getProfilePicUrl() == null) user.setProfilePicUrl(avatarUrl);
            }
            user = userRepository.save(user);
            log.info("OAuth user created/linked: {} via {}", resolvedEmail, normalizedProvider);
        }

        if (!user.getIsActive()) {
            throw new IllegalStateException("Account is deactivated");
        }
        if (Boolean.TRUE.equals(user.getIsSuspended())) {
            throw new IllegalStateException("Account is suspended");
        }

        user.setLastLoginAt(LocalDateTime.now());
        user = userRepository.save(user);

        String token = jwtUtils.generateAccessToken(
                user.getUserId(), user.getEmail(), user.getRole());
        String refreshToken = jwtUtils.generateRefreshToken(
                user.getUserId(), user.getEmail(), user.getRole());
        redisSessionService.cacheSession(user.getUserId(), token);
        return new AuthResponse(token, refreshToken, userMapper.toResponse(user));
    }

    // ── Admin Operations ──────────────────────────────────

    @Override
    public void suspendUser(Long userId) {
        User user = getUserById(userId);
        if ("ROLE_ADMIN".equals(user.getRole())) {
            throw new IllegalArgumentException("Cannot suspend another admin account");
        }
        user.setIsSuspended(true);
        userRepository.save(user);
        // Evict any active session so the user is blocked on next request
        redisSessionService.evictSession(userId);
        log.info("Account suspended by admin: userId={}", userId);
    }

    @Override
    public void reactivateUser(Long userId) {
        User user = getUserById(userId);
        user.setIsSuspended(false);
        user.setIsActive(true);
        userRepository.save(user);
        log.info("Account reactivated by admin: userId={}", userId);
    }

    @Override
    public void permanentlyDeleteUser(Long userId) {
        User user = getUserById(userId);
        // Evict session before deletion
        redisSessionService.evictSession(userId);
        userRepository.deleteByUserId(userId);
        log.info("Account permanently deleted by admin: userId={}", userId);
    }

    @Override
    @Transactional(readOnly = true)
    public UserStatsResponse getUserStats() {
        LocalDateTime startOfDay = LocalDateTime.now().toLocalDate().atStartOfDay();

        UserStatsResponse response = new UserStatsResponse();
        response.setTotalUsers(userRepository.count());
        response.setDailyActiveUsers(userRepository.countByLastLoginAtGreaterThanEqual(startOfDay));
        response.setAsOf(LocalDateTime.now());
        return response;
    }

    // ── Helpers ───────────────────────────────────────────

    private String resolveUniqueUsername(String base) {
        String prefix = StringUtils.hasText(base) ? base : "user";
        String candidate = prefix;
        int suffix = 1;
        while (userRepository.existsByUsername(candidate)) {
            candidate = prefix + suffix++;
        }
        return candidate;
    }

    private String resolveBaseUsername(String email, String name, String provider, String providerId) {
        if (StringUtils.hasText(email) && email.contains("@")) {
            return email.substring(0, email.indexOf("@")).replaceAll("[^a-zA-Z0-9_]", "");
        }
        if (StringUtils.hasText(name)) {
            String fromName = name.replaceAll("[^a-zA-Z0-9_]", "");
            if (StringUtils.hasText(fromName)) {
                return fromName.toLowerCase(Locale.ROOT);
            }
        }
        return (provider + providerId).replaceAll("[^a-zA-Z0-9_]", "").toLowerCase(Locale.ROOT);
    }

    private String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeUsername(String username) {
        return username == null ? null : username.trim();
    }
}
