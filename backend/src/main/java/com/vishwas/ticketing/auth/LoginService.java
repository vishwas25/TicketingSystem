package com.vishwas.ticketing.auth;

import java.util.Optional;

import org.springframework.stereotype.Service;

import com.vishwas.ticketing.auth.LoginController.LoginResponse;
import com.vishwas.ticketing.user.UserRepository;
import com.vishwas.ticketing.user.User;

@Service 
public class LoginService {
    
    private final UserRepository userRepository;

    private final PasswordEncodingConfig passwordEncodingConfig;

    private final JWTConfig jwtConfigService;

    public LoginService(UserRepository userRepository, PasswordEncodingConfig passwordEncodingConfig, JWTConfig jwtConfigService) {
        this.userRepository = userRepository;
        this.passwordEncodingConfig = passwordEncodingConfig;
        this.jwtConfigService = jwtConfigService;
    }

    public LoginResponse login(String email, String password) {
        Optional<User> user = userRepository.findByEmailIgnoreCase(email);
        if(user.isEmpty()) {
            return new LoginResponse(false, "User not found", null);
        }

        if(!passwordEncodingConfig.passwordEncoder().matches(password, user.get().getPasswordHash())) {
            return new LoginResponse(false, "Invalid password", null);
        }

        String token = jwtConfigService.generateJwtToken(user.get());

        return new LoginResponse(true, "Login successful", token);
    }    
}
