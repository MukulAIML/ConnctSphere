package com.connectsphere.auth.service;

import com.connectsphere.auth.dto.AuthDto.*;
import com.connectsphere.auth.entity.User;

import java.util.List;

/**
 * AuthService — business contract for identity and user management.
 *
 * Every protected operation on the ConnectSphere platform is gated
 * through the token validation method declared here.
 */
public interface AuthService {

    /** Register a new local user and return a signed JWT. */
    AuthResponse register(RegisterRequest request);

    /** Authenticate with email/password and return a signed JWT. */
    AuthResponse login(LoginRequest request);

    /** Invalidate the current session (stateless JWT — logs the JTI). */
    void logout(String token);

    /** Validate a JWT string; returns true if valid and not expired. */
    boolean validateToken(String token);

    /** Issue a fresh JWT from a valid, non-expired token. */
    String refreshToken(String token);

    /** Look up a user by email address. */
    User getUserByEmail(String email);

    /** Look up a user by primary key. */
    User getUserById(Long userId);

    /** Update profile fields (username, fullName, bio, profilePicUrl). */
    User updateProfile(Long userId, UpdateProfileRequest request);

    /** Change password after verifying the current password. */
    void changePassword(Long userId, ChangePasswordRequest request);

    /** Soft-deactivate (isActive = false) the given account. */
    void deactivateAccount(Long userId);

    /** Full-text search across username and fullName. */
    List<User> searchUsers(String query);

    /**
     * Find-or-create a user via OAuth2 (Google / GitHub).
     * Returns a signed JWT ready for use by the web layer.
     */
    AuthResponse handleOAuthLogin(String provider, String providerId,
                                  String email, String name, String avatarUrl);
}
