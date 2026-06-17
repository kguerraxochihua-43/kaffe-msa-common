package com.kaffe.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.Optional;

public class JwtTokenService {

    private final SecretKey signingKey;
    private final Clock clock;

    public JwtTokenService(String secret) {
        this(secret, Clock.systemUTC());
    }

    public JwtTokenService(String secret, Clock clock) {
        if (secret == null || secret.length() < 32) {
            throw new IllegalArgumentException("JWT secret must contain at least 32 characters for HS256");
        }
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.clock = clock;
    }

    public String generateToken(Long userId, String email, String role, long expirationMs) {
        return generateToken(
                email,
                Map.of(
                        "userId", String.valueOf(userId),
                        "email", email,
                        "role", role
                ),
                expirationMs
        );
    }

    public String generateToken(String subject, Map<String, ?> claims, long expirationMs) {
        Instant now = clock.instant();
        var builder = Jwts.builder()
                .subject(subject)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(expirationMs)))
                .signWith(signingKey);
        claims.forEach(builder::claim);
        return builder.compact();
    }

    public Optional<JwtPrincipal> parsePrincipal(String token) {
        try {
            Claims claims = parseClaims(token);
            String subject = claims.getSubject();
            String email = claims.get("email", String.class);
            String role = claims.get("role", String.class);
            Long userId = parseUserId(claims.get("userId", String.class));

            if (subject == null || subject.isBlank()) {
                return Optional.empty();
            }

            return Optional.of(new JwtPrincipal(subject, userId, email, role));
        } catch (JwtException | IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    public boolean isValid(String token) {
        return parsePrincipal(token).isPresent();
    }

    public Optional<String> extractSubject(String token) {
        try {
            return Optional.ofNullable(parseClaims(token).getSubject());
        } catch (JwtException | IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    public Optional<String> extractStringClaim(String token, String claimName) {
        try {
            return Optional.ofNullable(parseClaims(token).get(claimName, String.class));
        } catch (JwtException | IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    public boolean isValidPurposeToken(String token, String expectedSubject, String expectedPurpose) {
        try {
            Claims claims = parseClaims(token);
            String subject = claims.getSubject();
            String purpose = claims.get("purpose", String.class);
            return expectedSubject != null
                    && expectedSubject.equalsIgnoreCase(subject)
                    && expectedPurpose.equals(purpose);
        } catch (JwtException | IllegalArgumentException ex) {
            return false;
        }
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .clock(() -> Date.from(clock.instant()))
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private Long parseUserId(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return Long.valueOf(value);
    }
}
