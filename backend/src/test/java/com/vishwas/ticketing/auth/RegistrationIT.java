package com.vishwas.ticketing.auth;

import com.vishwas.ticketing.AbstractIntegrationTest;
import com.vishwas.ticketing.user.User;
import com.vishwas.ticketing.user.UserRepository;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(OutputCaptureExtension.class)
class RegistrationIT extends AbstractIntegrationTest {

    private static final String REGISTRATION_PATH = "/api/v1/auth/registration";

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TestRestTemplate testRestTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void registersUserWithEncodedPassword() {
        String email = UUID.randomUUID() + "@example.test";
        String rawPassword = "test-Password-@123";

        ResponseEntity<RegistrationController.RegistrationResponse> response = register(email, rawPassword);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();

        User savedUser = userRepository.findByEmailIgnoreCase(email).orElseThrow();
        assertThat(response.getBody().userId()).isEqualTo(savedUser.getId());
        assertThat(savedUser.getPasswordHash()).isNotEqualTo(rawPassword);
        assertThat(passwordEncoder.matches(rawPassword, savedUser.getPasswordHash())).isTrue();
        assertThat(response.getBody().toString()).doesNotContain(rawPassword, savedUser.getPasswordHash());
    }

    @Test
    void acceptsPasswordAtThe72ByteBoundary() {
        String email = UUID.randomUUID() + "@example.test";
        String password = "Aa1" + "b".repeat(69);

        ResponseEntity<RegistrationController.RegistrationResponse> response = register(email, password);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        User savedUser = userRepository.findByEmailIgnoreCase(email).orElseThrow();
        assertThat(passwordEncoder.matches(password, savedUser.getPasswordHash())).isTrue();
    }

    @Test
    void rejectsDuplicateEmailIgnoringCase() {
        String email = UUID.randomUUID() + "@example.test";

        assertThat(register(email, "test-Password-@123").getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<RegistrationController.RegistrationResponse> duplicateResponse =
                register(email.toUpperCase(Locale.ROOT), "test-Password-@123");

        assertThat(duplicateResponse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @ParameterizedTest
    @MethodSource("invalidRegistrationRequests")
    void rejectsInvalidRequestsWithoutLeakingInput(
            String requestBody,
            String expectedEmail,
            CapturedOutput output) {
        ResponseEntity<String> response = postJson(requestBody);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull()
            .doesNotContain("NoLog", "lowercaseonly");
        assertThat(output.getAll()).doesNotContain("NoLog", "lowercaseonly");
        if (expectedEmail != null) {
            assertThat(userRepository.findByEmailIgnoreCase(expectedEmail)).isEmpty();
        }
    }

    @Test
    void rejectsPasswordOver72Utf8BytesWithoutSavingUser() {
        String email = UUID.randomUUID() + "@example.test";
        String password = "Aa1" + "é".repeat(35);

        ResponseEntity<RegistrationController.RegistrationResponse> response = register(email, password);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull().extracting(RegistrationController.RegistrationResponse::message)
                .isEqualTo("Password must not exceed 72 UTF-8 bytes");
        assertThat(userRepository.findByEmailIgnoreCase(email)).isEmpty();
    }

    private static Stream<Arguments> invalidRegistrationRequests() {
        String email = "invalid-" + UUID.randomUUID() + "@example.test";
        return Stream.of(
                Arguments.of("{\"password\":\"NoLog\"}", null),
                Arguments.of("{\"email\":\"" + email + "\"}", email),
                Arguments.of("{\"email\":null,\"password\":\"NoLog\"}", null),
                Arguments.of("{\"email\":\"" + email + "\",\"password\":null}", email),
                Arguments.of("{\"email\":\" \",\"password\":\"NoLog\"}", null),
                Arguments.of("{\"email\":\"" + email + "\",\"password\":\" \"}", email),
                Arguments.of("{\"email\":\"not-an-email\",\"password\":\"NoLog\"}", null),
                Arguments.of("{\"email\":\"" + "a".repeat(250) + "@test\",\"password\":\"NoLog\"}", null),
                Arguments.of("{\"email\":\"" + email + "\",\"password\":\"NoLog\"}", email),
                Arguments.of("{\"email\":\"" + email + "\",\"password\":\"" + "A1" + "b".repeat(71) + "\"}", email),
                Arguments.of("{\"email\":\"" + email + "\",\"password\":\"lowercaseonly\"}", email));
    }

    private ResponseEntity<RegistrationController.RegistrationResponse> register(String email, String password) {
        return testRestTemplate.postForEntity(
                REGISTRATION_PATH,
                Map.of("email", email, "password", password),
                RegistrationController.RegistrationResponse.class);
    }

    private ResponseEntity<String> postJson(String requestBody) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return testRestTemplate.postForEntity(
                REGISTRATION_PATH,
                new HttpEntity<>(requestBody, headers),
                String.class);
    }
}