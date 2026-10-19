package com.vishwas.ticketing.auth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth/registration")
public class RegistrationController {

    private final RegistrationService registrationService;

    public RegistrationController(RegistrationService registrationService) {
        this.registrationService = registrationService;
    }

    public record RegistrationRequest(
            @NotBlank @Size(min = 5, max = 254) @Email String email,
            @NotBlank @Size(min = 8, max = 72) String password) {
    }

    public record RegistrationResponse(Long userId, String message) {
    }

    @PostMapping
    public ResponseEntity<RegistrationResponse> register(@Valid @RequestBody RegistrationRequest request) {
        try {
            Long userId = registrationService.register(request.email(), request.password());
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(new RegistrationResponse(userId, "User registration successful"));
        } catch (RegistrationException exception) {
            HttpStatus status = switch (exception.getReason()) {
                case INVALID_INPUT -> HttpStatus.BAD_REQUEST;
                case DUPLICATE_EMAIL -> HttpStatus.CONFLICT;
            };
            return ResponseEntity.status(status)
                    .body(new RegistrationResponse(null, exception.getMessage()));
        }
    }
}