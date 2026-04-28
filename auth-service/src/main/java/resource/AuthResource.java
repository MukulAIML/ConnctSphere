package com.connectsphere.auth.resource;

import com.connectsphere.auth.config.UserMapper;
import com.connectsphere.auth.dto.ApiResponse;
import com.connectsphere.auth.dto.AuthDto.*;
import com.connectsphere.auth.entity.User;
import com.connectsphere.auth.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Auth / User Service", description = "Registration, login, JWT, profile and user management")
public class AuthResource {

    private final AuthService authService;
    private final UserMapper  userMapper;

    @PostMapping("/register")
    @Operation(summary = "Register a new user with email and password")
    public ResponseEntity<ApiResponse<AuthResponse>> register(@Valid @RequestBody RegisterRequest request) {
        log.info("Received request to register user: {}", request.getEmail());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created("User registered successfully", authService.register(request)));
    }

    @PostMapping("/login")
    @Operation(summary = "Authenticate and receive a JWT")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
        log.info("Received request to login user");
        return ResponseEntity.ok(ApiResponse.success("User logged in successfully", authService.login(request)));
    }

    @PostMapping("/logout")
    @Operation(summary = "Logout — client should discard the token", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<Void>> logout(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        String token = extractBearerToken(authHeader);
        log.info("Received request to logout");
        authService.logout(token);
        return ResponseEntity.ok(ApiResponse.success("Logged out successfully", null));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Refresh a JWT before it expires")
    public ResponseEntity<ApiResponse<String>> refresh(@RequestHeader("Authorization") String authHeader) {
        String token = extractBearerToken(authHeader);
        log.info("Received request to refresh token");
        String newToken = authService.refreshToken(token);
        return ResponseEntity.ok(ApiResponse.success("Token refreshed successfully", newToken));
    }

    @GetMapping("/profile")
    @Operation(summary = "Get the authenticated user's profile", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<UserResponse>> getProfile(Authentication auth) {
        Long userId = resolveUserId(auth);
        log.info("Received request to fetch profile for user: {}", userId);
        return ResponseEntity.ok(ApiResponse.success("Profile fetched successfully", userMapper.toResponse(authService.getUserById(userId))));
    }

    @PutMapping("/profile")
    @Operation(summary = "Update username, bio, fullName, or profile picture", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<UserResponse>> updateProfile(Authentication auth, @Valid @RequestBody UpdateProfileRequest request) {
        Long userId = resolveUserId(auth);
        log.info("Received request to update profile for user: {}", userId);
        User updated = authService.updateProfile(userId, request);
        return ResponseEntity.ok(ApiResponse.success("Profile updated successfully", userMapper.toResponse(updated)));
    }

    @PutMapping("/password")
    @Operation(summary = "Change password (requires current password)", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<Void>> changePassword(Authentication auth, @Valid @RequestBody ChangePasswordRequest request) {
        Long userId = resolveUserId(auth);
        log.info("Received request to change password for user: {}", userId);
        authService.changePassword(userId, request);
        return ResponseEntity.ok(ApiResponse.success("Password changed successfully", null));
    }

    @DeleteMapping("/deactivate")
    @Operation(summary = "Soft-deactivate the authenticated user's account", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<Void>> deactivate(Authentication auth) {
        Long userId = resolveUserId(auth);
        log.info("Received request to deactivate account for user: {}", userId);
        authService.deactivateAccount(userId);
        return ResponseEntity.ok(ApiResponse.success("Account deactivated successfully", null));
    }

    @GetMapping("/search")
    @Operation(summary = "Search users by username or full name", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<List<UserResponse>>> search(
            @RequestParam(value = "q", required = false) String q,
            @RequestParam(value = "query", required = false) String queryParam
    ) {
        String query = (q != null && !q.isBlank()) ? q : (queryParam == null ? "" : queryParam);
        log.info("Received request to search users with query: {}", query);
        List<UserResponse> results = authService.searchUsers(query)
                .stream()
                .map(userMapper::toResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.success("Users searched successfully", results));
    }

    @GetMapping("/users/{id}")
    @Operation(summary = "Retrieve a user profile by ID", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<UserResponse>> getUserById(@PathVariable Long id) {
        log.info("Received request to fetch user profile for ID: {}", id);
        return ResponseEntity.ok(ApiResponse.success("User fetched successfully", userMapper.toResponse(authService.getUserById(id))));
    }

    @GetMapping("/admin/users")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "[Admin] List all users", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<List<UserResponse>>> listAllUsers() {
        log.info("Received request to list all users");
        List<UserResponse> all = authService.searchUsers("")
                .stream()
                .map(userMapper::toResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.success("All users fetched successfully", all));
    }

    private Long resolveUserId(Authentication auth) {
        if (auth == null || auth.getPrincipal() == null) {
            throw new IllegalStateException("No authenticated principal found");
        }
        return Long.valueOf(auth.getPrincipal().toString());
    }

    private String extractBearerToken(String header) {
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return header;
    }
}
