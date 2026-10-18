package com.vishwas.ticketing.auth;

import com.vishwas.ticketing.user.User;
import com.vishwas.ticketing.user.UserRepository;
import java.sql.SQLException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RegistrationServiceTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final RegistrationService registrationService =
            new RegistrationService(userRepository, passwordEncoder);

    @Test
    void translatesEmailUniqueIndexRaceToDuplicateEmail() {
        String email = "user@example.test";
        String password = "test-Password-@123";
        when(userRepository.findByEmailIgnoreCase(email)).thenReturn(Optional.empty());
        when(passwordEncoder.encode(password)).thenReturn("encoded-password");

        ConstraintViolationException databaseCause = new ConstraintViolationException(
                "duplicate email", new SQLException("duplicate key"), "idx_users_email");
        when(userRepository.saveAndFlush(any(User.class)))
                .thenThrow(new DataIntegrityViolationException("unique constraint violation", databaseCause));

        assertThatThrownBy(() -> registrationService.register(email, password))
                .isInstanceOfSatisfying(RegistrationException.class, exception ->
                        org.assertj.core.api.Assertions.assertThat(exception.getReason())
                                .isEqualTo(RegistrationException.Reason.DUPLICATE_EMAIL));
    }

        @Test
        void rejectsPasswordOverBcryptByteLimitBeforeEncoding() {
                String email = "user@example.test";
                String password = "Aa1" + "é".repeat(35);

                assertThat(password.getBytes(StandardCharsets.UTF_8)).hasSize(73);
                assertThatThrownBy(() -> registrationService.register(email, password))
                                .isInstanceOfSatisfying(RegistrationException.class, exception ->
                                                org.assertj.core.api.Assertions.assertThat(exception.getReason())
                                                                .isEqualTo(RegistrationException.Reason.INVALID_INPUT));
                verifyNoInteractions(userRepository, passwordEncoder);
        }
}