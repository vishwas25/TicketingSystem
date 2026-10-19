package com.vishwas.ticketing.auth;

import org.springframework.context.annotation.Configuration;

import com.vishwas.ticketing.user.User;

import java.util.Date;

import org.springframework.beans.factory.annotation.Value;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;

@Configuration 
public class JWTConfig {

    @Value("${JWT_KEY}")
    private String jwtKey;

    private long ttl = 15 * 60 * 1000; // 15 minutes in milliseconds

    public record validateJwtTokenResult(boolean isValid, String message) {}

    public String generateJwtToken(User user) {
        
        return Jwts.builder()
                   .subject(user.getEmail())
                   .claim("role", user.getRole())
                   .issuedAt(Date.from(java.time.Instant.now()))
                   .expiration(Date.from(java.time.Instant.now().plusMillis(ttl)))
                   .signWith(io.jsonwebtoken.security.Keys.hmacShaKeyFor(jwtKey.getBytes()))
                   .compact();
    }

    public validateJwtTokenResult validateJwtToken(User user, String token) {
        Claims claims = Jwts.parser()
                            .verifyWith(io.jsonwebtoken.security.Keys.hmacShaKeyFor(jwtKey.getBytes()))
                            .clockSkewSeconds(30)
                            .clock(() -> Date.from(java.time.Instant.now()))
                            .build()
                            .parseSignedClaims(token)
                            .getPayload();
        
        if(!claims.getSubject().equals(user.getEmail())) {
            return new validateJwtTokenResult(false, "Invalid JWT token for user: " + user.getEmail());
        }
        if(!claims.get("role", String.class).equals(user.getRole())) {
            return new validateJwtTokenResult(false, "Invalid JWT token for user role: " + user.getRole());
        }
        return new validateJwtTokenResult(true, "Valid JWT token");
    }
}