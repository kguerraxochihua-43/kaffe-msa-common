package com.kaffe.common.security;

public record JwtPrincipal(
        String subject,
        Long userId,
        String email,
        String role
) {
}
