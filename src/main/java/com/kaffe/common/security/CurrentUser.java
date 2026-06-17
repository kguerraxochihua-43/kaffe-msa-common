package com.kaffe.common.security;

public record CurrentUser(
        String subject,
        Long userId,
        String email,
        String role,
        boolean authenticated
) {
}
