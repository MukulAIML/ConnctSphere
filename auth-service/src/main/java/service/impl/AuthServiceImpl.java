package com.connectsphere.auth.service.impl;

import com.connectsphere.auth.config.UserMapper;
import com.connectsphere.auth.dto.AuthDto.*;
import com.connectsphere.auth.entity.User;
import com.connectsphere.auth.repository.UserRepository;
import com.connectsphere.auth.security.JwtUtils;
import com.connectsphere.auth.service.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

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

    // ── Register ──────────────────────────────────────────

    @Override
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException(
                "Email already in use: " + request.getEmail());
        }
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new IllegalArgumentException(
                "Username already taken: " + request.getUsername());
        }

        User user = User.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .fullName(request.getFullName())
                .provider("LOCAL")
                .build();

        user = userRepository.save(user);
        log.info("New user registered: {}", user.getEmail());

        String token = jwtUtils.generateToken(
                user.getUserId(), user.getEmail(), user.getRole());
        return new AuthResponse(token, userMapper.toResponse(user));
    }

    // ── Login ─────────────────────────────────────────────

    @Override
    public AuthResponse login(LoginRequest request) {
        // Find by email or username
        User user = userRepository.findByEmail(request.getEmail())
                .or(() -> userRepository.findByUsername(request.getEmail()))
                .orElseThrow(() -> new IllegalArgumentException(
                        "Invalid email/username or password"));

        if (!user.getIsActive()) {
            throw new IllegalStateException("Account is deactivated");
        }
        if (user.getPasswordHash() == null ||
                !passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException("Invalid email or password");
        }

        String token = jwtUtils.generateToken(
                user.getUserId(), user.getEmail(), user.getRole());
        log.debug("User logged in: {}", user.getEmail());
        return new AuthResponse(token, userMapper.toResponse(user));
    }

    // ── Logout (stateless — client discards token) ────────

    @Override
    public void logout(String token) {
        // For full token revocation, persist JTI in a Redis blocklist here.
        // For now, clients are responsible for discarding the token.
        log.debug("Logout called — client must discard token");
    }

    // ── Token Validation & Refresh ────────────────────────

    @Override
    @Transactional(readOnly = true)
    public boolean validateToken(String token) {
        return jwtUtils.validateToken(token);
    }

    @Override
    @Transactional(readOnly = true)
    public String refreshToken(String token) {
        if (!jwtUtils.validateToken(token)) {
            throw new IllegalArgumentException("Token is invalid or expired");
        }
        return jwtUtils.refreshToken(token);
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

        if (request.getUsername() != null &&
                !request.getUsername().equals(user.getUsername())) {
            if (userRepository.existsByUsername(request.getUsername())) {
                throw new IllegalArgumentException(
                    "Username already taken: " + request.getUsername());
            }
            user.setUsername(request.getUsername());
        }
        if (request.getFullName()    != null) user.setFullName(request.getFullName());
        if (request.getBio()         != null) user.setBio(request.getBio());
        if (request.getProfilePicUrl() != null) user.setProfilePicUrl(request.getProfilePicUrl());

        return userRepository.save(user);
    }

    @Override
    public void changePassword(Long userId, ChangePasswordRequest request) {
        User user = getUserById(userId);

        if (user.getPasswordHash() == null ||
                !passwordEncoder.matches(request.getCurrentPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException("Current password is incorrect");
        }

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
        log.info("Password changed for userId={}", userId);
    }

    // ── Account Deactivation ──────────────────────────────

    @Override
    public void deactivateAccount(Long userId) {
        User user = getUserById(userId);
        user.setIsActive(false);
        userRepository.save(user);
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
        // 1. Try to find by provider + providerId
        User user = userRepository.findByProviderAndProviderId(provider, providerId)
                .orElse(null);

        if (user == null) {
            // 2. Try to find by email (link OAuth to existing local account)
            user = userRepository.findByEmail(email).orElse(null);

            if (user == null) {
                // 3. Create brand-new OAuth user
                String baseUsername = email.split("@")[0].replaceAll("[^a-zA-Z0-9_]", "");
                String username     = resolveUniqueUsername(baseUsername);

                user = User.builder()
                        .username(username)
                        .email(email)
                        .fullName(name)
                        .profilePicUrl(avatarUrl)
                        .provider(provider.toUpperCase())
                        .providerId(providerId)
                        .build();
            } else {
                // Link OAuth credentials to existing account
                user.setProvider(provider.toUpperCase());
                user.setProviderId(providerId);
                if (user.getProfilePicUrl() == null) user.setProfilePicUrl(avatarUrl);
            }
            user = userRepository.save(user);
            log.info("OAuth user created/linked: {} via {}", email, provider);
        }

        if (!user.getIsActive()) {
            throw new IllegalStateException("Account is deactivated");
        }

        String token = jwtUtils.generateToken(
                user.getUserId(), user.getEmail(), user.getRole());
        return new AuthResponse(token, userMapper.toResponse(user));
    }

    // ── Helpers ───────────────────────────────────────────

    private String resolveUniqueUsername(String base) {
        String candidate = base;
        int suffix = 1;
        while (userRepository.existsByUsername(candidate)) {
            candidate = base + suffix++;
        }
        return candidate;
    }
}
