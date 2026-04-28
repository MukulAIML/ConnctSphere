package com.connectsphere.auth.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.time.LocalDateTime;

/**
 * User — core identity entity for the ConnectSphere platform.
 * Stores credentials, profile info, role, OAuth provider, and account state.
 */
@Entity
@Table(
    name = "users",
    uniqueConstraints = {
        @UniqueConstraint(columnNames = "email"),
        @UniqueConstraint(columnNames = "username")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = "passwordHash")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long userId;

    @NotBlank
    @Size(min = 3, max = 50)
    @Column(nullable = false, unique = true, length = 50)
    private String username;

    @NotBlank
    @Email
    @Column(nullable = false, unique = true, length = 100)
    private String email;

    /** Bcrypt hash — null for OAuth-only users. */
    @Column(length = 255)
    private String passwordHash;

    @Size(max = 100)
    @Column(length = 100)
    private String fullName;

    @Size(max = 300)
    @Column(length = 300)
    private String bio;

    @Column(length = 500)
    private String profilePicUrl;

    /**
     * Role: ROLE_USER | ROLE_ADMIN | ROLE_MODERATOR
     */
    @Column(nullable = false, length = 20)
    @Builder.Default
    private String role = "ROLE_USER";

    /**
     * Auth provider: LOCAL | GOOGLE | GITHUB
     */
    @Column(nullable = false, length = 10)
    @Builder.Default
    private String provider = "LOCAL";

    /** OAuth2 provider-specific subject ID */
    @Column(length = 200)
    private String providerId;

    @Column(nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @Column(nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column
    private LocalDateTime updatedAt;

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
