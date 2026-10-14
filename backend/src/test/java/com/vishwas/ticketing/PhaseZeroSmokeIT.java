package com.vishwas.ticketing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.exception.FlywayValidateException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;

class PhaseZeroSmokeIT extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private TestRestTemplate http;

    @Test
    void appliesBaselineMigrationToRealPostgres() {
        assertThat(jdbc.queryForObject("select version()", String.class)).contains("PostgreSQL 16");
        assertThat(jdbc.queryForObject(
                "select count(*) from flyway_schema_history where version = '1' and success", Long.class))
                .isEqualTo(1L);
    }

        @Test
        void detectsAnAlteredMigrationWithoutChangingTheAppliedFile() {
        var alteredMigration = Flyway.configure()
            .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
            .locations("classpath:db/checksum-mismatch")
            .load();
        assertThatThrownBy(alteredMigration::validate)
            .isInstanceOf(FlywayValidateException.class)
            .hasMessageContaining("checksum mismatch");
        }

        @Test
        void persistsUserDefaultsAndTimezoneAwareTimestamps() {
        String email = UUID.randomUUID() + "@example.test";
        Long userId = jdbc.queryForObject(
            "insert into users (email, password_hash) values (?, ?) returning id",
            Long.class, email, "test-only");
        var row = jdbc.queryForMap(
            "select role, active, pg_typeof(created_at)::text as timestamp_type from users where id = ?",
            userId);
        assertThat(row).containsEntry("role", "USER").containsEntry("active", true)
            .containsEntry("timestamp_type", "timestamp with time zone");
        }

    @Test
    void rejectsAnInvalidRoleAtTheDatabaseBoundary() {
        String email = UUID.randomUUID() + "@example.test";
        assertThatThrownBy(() -> jdbc.update(
                "insert into users (email, password_hash, role) values (?, ?, ?)",
                email, "test-only-not-a-real-password", "SUPERUSER"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("chk_users_role");
    }

    @Test
    void rejectsDuplicateEmailIgnoringCase() {
        String email = UUID.randomUUID() + "@example.test";
        jdbc.update("insert into users (email, password_hash) values (?, ?)", email, "test-only");
        assertThatThrownBy(() -> jdbc.update(
                "insert into users (email, password_hash) values (?, ?)", email.toUpperCase(), "test-only"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("idx_users_email");
    }

    @Test
    void exposesHealthWithoutDatabaseDetails() {
        var response = http.getForEntity("/actuator/health", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"status\":\"UP\"").doesNotContain("components");
    }

    @Test
    void exposesReadinessAndLiveness() {
        assertThat(http.getForEntity("/actuator/health/readiness", String.class).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(http.getForEntity("/actuator/health/liveness", String.class).getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }

    @Test
    void publishesOpenApiAndSwaggerUi() {
        var api = http.getForEntity("/v3/api-docs", String.class);
        assertThat(api.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(api.getBody()).contains("Ticketing System API");
        assertThat(http.getForEntity("/swagger-ui/index.html", String.class).getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }
}