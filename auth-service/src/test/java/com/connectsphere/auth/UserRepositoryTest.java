package com.connectsphere.auth.repository;

import com.connectsphere.auth.entity.User;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.*;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@DisplayName("UserRepository — Integration Tests (H2)")
class UserRepositoryTest {

    @Autowired UserRepository   userRepository;
    @Autowired TestEntityManager em;

    private User persistUser(String username, String email) {
        User u = User.builder()
                .username(username)
                .email(email)
                .passwordHash("$2a$12$hash")
                .role("ROLE_USER")
                .provider("LOCAL")
                .isActive(true)
                .build();
        return em.persistAndFlush(u);
    }

    // ── findByEmail ───────────────────────────────────────

    @Test
    @DisplayName("findByEmail — returns user for known email")
    void findByEmailFound() {
        persistUser("alice", "alice@example.com");
        Optional<User> found = userRepository.findByEmail("alice@example.com");
        assertThat(found).isPresent();
        assertThat(found.get().getUsername()).isEqualTo("alice");
    }

    @Test
    @DisplayName("findByEmail — empty for unknown email")
    void findByEmailMissing() {
        assertThat(userRepository.findByEmail("unknown@example.com")).isEmpty();
    }

    // ── findByUsername ────────────────────────────────────

    @Test
    @DisplayName("findByUsername — returns user for known username")
    void findByUsernameFound() {
        persistUser("bob", "bob@example.com");
        assertThat(userRepository.findByUsername("bob")).isPresent();
    }

    // ── existsByEmail / existsByUsername ──────────────────

    @Test
    @DisplayName("existsByEmail — true when email exists")
    void existsByEmailTrue() {
        persistUser("charlie", "charlie@example.com");
        assertThat(userRepository.existsByEmail("charlie@example.com")).isTrue();
    }

    @Test
    @DisplayName("existsByEmail — false when email missing")
    void existsByEmailFalse() {
        assertThat(userRepository.existsByEmail("nobody@example.com")).isFalse();
    }

    @Test
    @DisplayName("existsByUsername — true when username exists")
    void existsByUsernameTrue() {
        persistUser("dave", "dave@example.com");
        assertThat(userRepository.existsByUsername("dave")).isTrue();
    }

    // ── searchByUsername ──────────────────────────────────

    @Test
    @DisplayName("searchByUsername — case-insensitive match on username")
    void searchUsername() {
        persistUser("AliceSmith", "alice.smith@example.com");
        persistUser("bob",        "bob@example.com");

        List<User> results = userRepository.searchByUsername("alice");
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getUsername()).isEqualTo("AliceSmith");
    }

    @Test
    @DisplayName("searchByUsername — matches on fullName field")
    void searchFullName() {
        User u = User.builder()
                .username("xyz123")
                .email("xyz@example.com")
                .passwordHash("h")
                .fullName("John Travolta")
                .role("ROLE_USER")
                .provider("LOCAL")
                .isActive(true)
                .build();
        em.persistAndFlush(u);

        List<User> results = userRepository.searchByUsername("travolta");
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getFullName()).isEqualTo("John Travolta");
    }

    @Test
    @DisplayName("searchByUsername — empty list for no match")
    void searchNoMatch() {
        persistUser("eve", "eve@example.com");
        assertThat(userRepository.searchByUsername("zzz")).isEmpty();
    }

    // ── findAllByRole ─────────────────────────────────────

    @Test
    @DisplayName("findAllByRole — returns only admin users")
    void findByRole() {
        persistUser("user1", "user1@example.com");
        User admin = User.builder()
                .username("adminUser")
                .email("admin@example.com")
                .passwordHash("h")
                .role("ROLE_ADMIN")
                .provider("LOCAL")
                .isActive(true)
                .build();
        em.persistAndFlush(admin);

        List<User> admins = userRepository.findAllByRole("ROLE_ADMIN");
        assertThat(admins).hasSize(1);
        assertThat(admins.get(0).getUsername()).isEqualTo("adminUser");
    }

    // ── findByProviderAndProviderId ───────────────────────

    @Test
    @DisplayName("findByProviderAndProviderId — finds OAuth user")
    void findOAuthUser() {
        User u = User.builder()
                .username("googleuser")
                .email("guser@gmail.com")
                .provider("GOOGLE")
                .providerId("google-sub-999")
                .role("ROLE_USER")
                .isActive(true)
                .build();
        em.persistAndFlush(u);

        Optional<User> found = userRepository
                .findByProviderAndProviderId("GOOGLE", "google-sub-999");
        assertThat(found).isPresent();
        assertThat(found.get().getEmail()).isEqualTo("guser@gmail.com");
    }
}
