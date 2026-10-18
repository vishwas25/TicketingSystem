package com.vishwas.ticketing.user;

import com.vishwas.ticketing.AbstractIntegrationTest;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.Locale;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

class UserPersistenceIT extends AbstractIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    void savesAndReloadsUserFromPostgres() {
        String email = UUID.randomUUID() + "@example.test";
        User savedUser = userRepository.saveAndFlush(new User(email, "test-hash", UserRole.USER));

        entityManager.clear();

        User reloadedUser = userRepository.findByEmailIgnoreCase(email.toUpperCase(Locale.ROOT))
                .orElseThrow();

        assertThat(reloadedUser.getId()).isEqualTo(savedUser.getId());
        assertThat(reloadedUser.getEmail()).isEqualTo(email);
        assertThat(reloadedUser.getPasswordHash()).isEqualTo("test-hash");
        assertThat(reloadedUser.getRole()).isEqualTo(UserRole.USER);
        assertThat(reloadedUser.isActive()).isTrue();
        assertThat(reloadedUser.getCreatedAt()).isNotNull();
        assertThat(reloadedUser.getUpdatedAt()).isEqualTo(reloadedUser.getCreatedAt());
    }
}