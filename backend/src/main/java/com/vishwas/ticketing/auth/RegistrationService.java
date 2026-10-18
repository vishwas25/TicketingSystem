package com.vishwas.ticketing.auth;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.vishwas.ticketing.user.User;
import com.vishwas.ticketing.user.UserRepository;
import java.nio.charset.StandardCharsets;

@Service
public class RegistrationService {

    private static final String EMAIL_UNIQUE_INDEX = "idx_users_email";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public RegistrationService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public Long register(String email, String password) {
        String validationError = verifyRegistrationInputs(email, password);
        if (validationError != null) {
            throw new RegistrationException(RegistrationException.Reason.INVALID_INPUT, validationError);
        }

        if (userRepository.findByEmailIgnoreCase(email).isPresent()) {
            throw duplicateEmailException();
        }

        User newUser = new User(email, passwordEncoder.encode(password));
        try {
            return userRepository.saveAndFlush(newUser).getId();
        } catch (DataIntegrityViolationException exception) {
            if (isEmailUniqueViolation(exception)) {
                throw duplicateEmailException(exception);
            }
            throw exception;
        }
    }

    private String verifyRegistrationInputs(String email, String password) {
        if (email == null || email.isBlank()) {
            return "Email cannot be empty";
        }

        if (email.length() > 254) {
            return "Email must not exceed 254 characters";
        }

        if (password == null || password.isBlank()) {
            return "Password cannot be empty";
        }

        if (password.length() < 8 || password.length() > 72) {
            return "Password must be between 8 and 72 characters";
        }

        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            return "Password must not exceed 72 UTF-8 bytes";
        }

        if (!password.matches("^(?=.*[A-Z])(?=.*[a-z])(?=.*\\d).+$")) {
            return "Password must contain at least one uppercase letter, one lowercase letter, and one number";
        }
        return null;
    }

    private boolean isEmailUniqueViolation(Throwable exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation) {
                return EMAIL_UNIQUE_INDEX.equals(violation.getConstraintName());
            }
        }
        return false;
    }

    private RegistrationException duplicateEmailException() {
        return new RegistrationException(
                RegistrationException.Reason.DUPLICATE_EMAIL,
                "An account with this email already exists");
    }

    private RegistrationException duplicateEmailException(Throwable cause) {
        return new RegistrationException(
                RegistrationException.Reason.DUPLICATE_EMAIL,
                "An account with this email already exists",
                cause);
    }
}