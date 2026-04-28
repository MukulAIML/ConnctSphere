package com.connectsphere.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/** ── Request DTOs ─────────────────────────────────────── */

public class AuthDto {

    // ── Register ─────────────────────────────────────────
    @Data
    public static class RegisterRequest {
        @NotBlank @Size(min = 3, max = 50)
        private String username;

        @NotBlank @Email
        private String email;

        @NotBlank @Size(min = 6, max = 100)
        private String password;

        @Size(max = 100)
        private String fullName;
    }

    // ── Login ────────────────────────────────────────────
    @Data
    public static class LoginRequest {
        @NotBlank
        private String email;       // email or username

        @NotBlank
        private String password;
    }

    // ── Update Profile ───────────────────────────────────
    @Data
    public static class UpdateProfileRequest {
        @Size(min = 3, max = 50)
        private String username;

        @Size(max = 100)
        private String fullName;

        @Size(max = 300)
        private String bio;

        @Size(max = 500)
        private String profilePicUrl;
    }

    // ── Change Password ──────────────────────────────────
    @Data
    public static class ChangePasswordRequest {
        @NotBlank
        private String currentPassword;

        @NotBlank @Size(min = 6, max = 100)
        private String newPassword;
    }

    /** ── Response DTOs ──────────────────────────────────── */

    // ── Auth Response (login / register) ─────────────────
    @Data
    public static class AuthResponse {
        private String token;
        private String tokenType = "Bearer";
        private UserResponse user;

        public AuthResponse(String token, UserResponse user) {
            this.token = token;
            this.user  = user;
        }
    }

    // ── User Profile Response ─────────────────────────────
    @Data
    public static class UserResponse {
        private Long userId;
        private String  username;
        private String  email;
        private String  fullName;
        private String  bio;
        private String  profilePicUrl;
        private String  role;
        private String  provider;
        private Boolean isActive;
        private LocalDateTime createdAt;
    }

    // ── Generic message ──────────────────────────────────
    @Data
    public static class MessageResponse {
        private String message;
        public MessageResponse(String message) { this.message = message; }
    }

    // ── Search result ─────────────────────────────────────
    @Data
    public static class SearchResponse {
        private List<UserResponse> users;
        private int total;
        public SearchResponse(List<UserResponse> users) {
            this.users = users;
            this.total = users.size();
        }
    }
}
