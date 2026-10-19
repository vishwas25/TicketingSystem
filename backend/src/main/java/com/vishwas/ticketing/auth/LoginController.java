package com.vishwas.ticketing.auth;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@RestController 
@RequestMapping("/api/v1/auth/login")
public class LoginController {

    private final LoginService loginService;

    public LoginController(LoginService loginService) {
        this.loginService = loginService;
    }
    
    public record LoginRequest(
        @NotBlank @Size(min = 5, max = 254) @Email String email,
        @NotBlank @Size(min = 8, max = 72) String password) {}

    public record LoginResponse(boolean success, String message, String token) {}

    @PostMapping 
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest loginRequest) { 
        
        LoginResponse response = loginService.login(loginRequest.email(), loginRequest.password());
        
        if(!response.success()) {
            return ResponseEntity.badRequest().body(response);
        }
        return ResponseEntity.ok(response);
    }
}
