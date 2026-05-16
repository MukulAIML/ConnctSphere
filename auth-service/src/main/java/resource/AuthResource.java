package com.connectsphere.auth.resource;

import com.connectsphere.auth.config.UserMapper;
import com.connectsphere.auth.dto.ApiResponse;
import com.connectsphere.auth.dto.AuthDto.*;
import com.connectsphere.auth.entity.User;
import com.connectsphere.auth.security.JwtUtils;
import com.connectsphere.auth.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
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
    private final JwtUtils    jwtUtils;

    // ── Public Endpoints ──────────────────────────────────

    @PostMapping("/register")
    @Operation(
        summary = "Register a new user",
        description = "Creates a new local user account with email and password. Returns a signed JWT on success."
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "User registered successfully"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation error or email/username already taken", content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    public ResponseEntity<ApiResponse<AuthResponse>> register(@Valid @RequestBody RegisterRequest request) {
        log.info("Received request to register user: {}", request.getEmail());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created("User registered successfully", authService.register(request)));
    }

    @PostMapping("/login")
    @Operation(
        summary = "Authenticate and receive a JWT",
        description = "Validates email/password credentials and returns a Bearer token along with the user profile."
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Login successful"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid credentials or account deactivated/suspended")
    })
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
        log.info("Received request to login user");
        return ResponseEntity.ok(ApiResponse.success("User logged in successfully", authService.login(request)));
    }

    @GetMapping("/oauth2/{provider}")
    @Operation(
        summary = "Start OAuth2 login flow",
        description = "Redirects to Spring Security OAuth2 authorization endpoint for Google or GitHub."
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "302", description = "Redirected to provider authorization page"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Unsupported provider")
    })
    public ResponseEntity<Void> startOAuthLogin(@PathVariable String provider) {
        String normalized = provider == null ? "" : provider.trim().toLowerCase();
        if (!"google".equals(normalized) && !"github".equals(normalized)) {
            throw new IllegalArgumentException("Unsupported OAuth provider: " + provider);
        }
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create("/oauth2/authorization/" + normalized))
                .build();
    }

    @PostMapping("/logout")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    @Operation(
        summary = "Logout — invalidates the session token",
        description = "Adds the current JWT to the Redis blocklist and evicts the session cache. The client must also discard the token.",
        security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Logged out successfully"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public ResponseEntity<ApiResponse<Void>> logout(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        String token = extractBearerToken(authHeader);
        log.info("Received request to logout");
        authService.logout(token);
        return ResponseEntity.ok(ApiResponse.success("Logged out successfully", null));
    }

    @PostMapping("/refresh")
    @Operation(
        summary = "Refresh a JWT before it expires",
        description = "Issues a new JWT with a fresh expiry from a valid, non-expired token.",
        security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Token refreshed successfully"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Token is invalid or expired")
    })
    public ResponseEntity<ApiResponse<String>> refresh(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestParam(value = "token", required = false) String tokenParam) {
        String token = StringUtils.hasText(tokenParam) ? tokenParam : extractBearerToken(authHeader);
        if (!StringUtils.hasText(token)) {
            throw new IllegalArgumentException("Refresh token is required");
        }
        log.info("Received request to refresh token");
        String newToken = authService.refreshToken(token);
        return ResponseEntity.ok(ApiResponse.success("Token refreshed successfully", newToken));
    }

    @PostMapping("/validate")
    @Operation(
        summary = "Validate a JWT",
        description = "Validates token signature/expiry and checks account state. Accepts token via Authorization header or token query parameter."
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Validation completed"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Missing token")
    })
    public ResponseEntity<ApiResponse<TokenValidationResponse>> validateToken(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestParam(value = "token", required = false) String tokenParam) {
        String token = StringUtils.hasText(tokenParam) ? tokenParam : extractBearerToken(authHeader);
        if (!StringUtils.hasText(token)) {
            throw new IllegalArgumentException("Token is required for validation");
        }

        boolean valid = authService.validateToken(token);
        TokenValidationResponse payload = new TokenValidationResponse();
        payload.setValid(valid);
        if (valid) {
            payload.setUserId(jwtUtils.getUserIdFromToken(token));
            payload.setEmail(jwtUtils.getEmailFromToken(token));
            payload.setRole(jwtUtils.getRoleFromToken(token));
            payload.setTokenType(jwtUtils.getTokenTypeFromToken(token));
        }
        return ResponseEntity.ok(ApiResponse.success("Token validation completed", payload));
    }

    // ── Authenticated User Endpoints ──────────────────────

    @GetMapping("/profile")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    @Operation(
        summary = "Get the authenticated user's profile",
        description = "Returns full profile details for the currently authenticated user.",
        security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Profile fetched successfully"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public ResponseEntity<ApiResponse<UserResponse>> getProfile(Authentication auth) {
        Long userId = resolveUserId(auth);
        log.info("Received request to fetch profile for user: {}", userId);
        return ResponseEntity.ok(ApiResponse.success("Profile fetched successfully", userMapper.toResponse(authService.getUserById(userId))));
    }

    @PutMapping("/profile")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    @Operation(
        summary = "Update user profile",
        description = "Update one or more profile fields: username, email, bio, fullName, or profilePicUrl. Only provided fields are changed.",
        security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Profile updated successfully"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Username already taken or validation error"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public ResponseEntity<ApiResponse<UserResponse>> updateProfile(Authentication auth, @Valid @RequestBody UpdateProfileRequest request) {
        Long userId = resolveUserId(auth);
        log.info("Received request to update profile for user: {}", userId);
        User updated = authService.updateProfile(userId, request);
        return ResponseEntity.ok(ApiResponse.success("Profile updated successfully", userMapper.toResponse(updated)));
    }

    @PutMapping("/password")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    @Operation(
        summary = "Change password",
        description = "Changes the user's password after verifying the current password. Requires a LOCAL provider account.",
        security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Password changed successfully"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Current password is incorrect"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public ResponseEntity<ApiResponse<Void>> changePassword(Authentication auth, @Valid @RequestBody ChangePasswordRequest request) {
        Long userId = resolveUserId(auth);
        log.info("Received request to change password for user: {}", userId);
        authService.changePassword(userId, request);
        return ResponseEntity.ok(ApiResponse.success("Password changed successfully", null));
    }

    @DeleteMapping("/deactivate")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    @Operation(
        summary = "Self-deactivate account",
        description = "Soft-deactivates the authenticated user's own account. The account can be reactivated by an admin. This is a self-service operation and does NOT permanently delete data.",
        security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Account deactivated successfully"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public ResponseEntity<ApiResponse<Void>> deactivate(
            Authentication auth,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        Long userId = resolveUserId(auth);
        log.info("Received request to deactivate account for user: {}", userId);
        authService.deactivateAccount(userId);
        authService.logout(extractBearerToken(authHeader));
        return ResponseEntity.ok(ApiResponse.success("Account deactivated successfully", null));
    }

    @GetMapping("/search")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    @Operation(
        summary = "Search users by username or full name",
        description = "Case-insensitive full-text search across username and fullName fields. Use query param 'q' or 'query'.",
        security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Search completed"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public ResponseEntity<ApiResponse<List<UserResponse>>> search(
            @Parameter(description = "Search term (shorthand)") @RequestParam(value = "q", required = false) String q,
            @Parameter(description = "Search term (alternative)") @RequestParam(value = "query", required = false) String queryParam
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
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    @Operation(
        summary = "Retrieve a user profile by ID",
        description = "Fetches the public profile of any user by their numeric ID. Available to all authenticated users.",
        security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "User fetched successfully"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "User not found"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public ResponseEntity<ApiResponse<UserResponse>> getUserById(
            @Parameter(description = "Numeric user ID", required = true) @PathVariable Long id) {
        log.info("Received request to fetch user profile for ID: {}", id);
        return ResponseEntity.ok(ApiResponse.success("User fetched successfully", userMapper.toResponse(authService.getUserById(id))));
    }

    // ── Admin Endpoints ───────────────────────────────────

    @GetMapping("/admin/users")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
        summary = "[Admin] List all users",
        description = "Returns all registered users. Requires ROLE_ADMIN.",
        security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "All users fetched successfully"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden — requires ROLE_ADMIN")
    })
    public ResponseEntity<ApiResponse<List<UserResponse>>> listAllUsers() {
        log.info("Received request to list all users");
        List<UserResponse> all = authService.searchUsers("")
                .stream()
                .map(userMapper::toResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.success("All users fetched successfully", all));
    }

    @GetMapping("/admin/stats/users")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
        summary = "[Admin] Get user platform stats",
        description = "Returns aggregate identity metrics needed by the admin dashboard, including total users and daily active users.",
        security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Stats fetched successfully"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden — requires ROLE_ADMIN")
    })
    public ResponseEntity<ApiResponse<UserStatsResponse>> getUserStats() {
        log.info("Received request to fetch admin user stats");
        return ResponseEntity.ok(ApiResponse.success("User stats fetched successfully", authService.getUserStats()));
    }

    @PatchMapping("/admin/users/{id}/suspend")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
        summary = "[Admin] Suspend a user account",
        description = "Sets the isSuspended flag to true and evicts the user's Redis session. " +
                      "The user cannot log in while suspended. Cannot be applied to other admins. Requires ROLE_ADMIN.",
        security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "User suspended successfully"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "User not found or attempt to suspend an admin"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden — requires ROLE_ADMIN")
    })
    public ResponseEntity<ApiResponse<Void>> suspendUser(
            @Parameter(description = "Numeric user ID to suspend", required = true) @PathVariable Long id) {
        log.info("Admin request to suspend userId={}", id);
        authService.suspendUser(id);
        return ResponseEntity.ok(ApiResponse.success("User suspended successfully", null));
    }

    @PatchMapping("/admin/users/{id}/reactivate")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
        summary = "[Admin] Reactivate a suspended or deactivated user",
        description = "Clears both isSuspended and isActive=false flags, allowing the user to log in again. Requires ROLE_ADMIN.",
        security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "User reactivated successfully"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "User not found"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden — requires ROLE_ADMIN")
    })
    public ResponseEntity<ApiResponse<Void>> reactivateUser(
            @Parameter(description = "Numeric user ID to reactivate", required = true) @PathVariable Long id) {
        log.info("Admin request to reactivate userId={}", id);
        authService.reactivateUser(id);
        return ResponseEntity.ok(ApiResponse.success("User reactivated successfully", null));
    }

    @DeleteMapping("/admin/users/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
        summary = "[Admin] Permanently delete a user account",
        description = "Hard-deletes the user record from the database and evicts their Redis session. " +
                      "This action is irreversible. Requires ROLE_ADMIN.",
        security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "User permanently deleted"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "User not found"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden — requires ROLE_ADMIN")
    })
    public ResponseEntity<ApiResponse<Void>> permanentlyDeleteUser(
            @Parameter(description = "Numeric user ID to permanently delete", required = true) @PathVariable Long id) {
        log.info("Admin request to permanently delete userId={}", id);
        authService.permanentlyDeleteUser(id);
        return ResponseEntity.ok(ApiResponse.success("User permanently deleted", null));
    }

    // ── Helpers ───────────────────────────────────────────

    private Long resolveUserId(Authentication auth) {
        if (auth == null || auth.getPrincipal() == null) {
            auth = SecurityContextHolder.getContext().getAuthentication();
        }
        if (auth == null || auth.getPrincipal() == null) {
            throw new IllegalStateException("No authenticated principal found");
        }
        Object principal = auth.getPrincipal();
        if (principal instanceof Number number) {
            return number.longValue();
        }
        if (principal instanceof String stringPrincipal) {
            try {
                return Long.valueOf(stringPrincipal);
            } catch (NumberFormatException ignored) {
                // fall through and try other principal types
            }
        }
        if (principal instanceof UserDetails userDetails) {
            try {
                return Long.valueOf(userDetails.getUsername());
            } catch (NumberFormatException ignored) {
                // fall through
            }
        }
        throw new IllegalStateException("Authenticated principal does not contain a numeric user id");
    }

    private String extractBearerToken(String header) {
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return header;
    }
}
