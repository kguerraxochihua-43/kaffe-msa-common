package com.kaffe.common.security;

public record KaffeAuthenticationDetails(
        JwtPrincipal principal,
        Object webAuthenticationDetails
) {
}
