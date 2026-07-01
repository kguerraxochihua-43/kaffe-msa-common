package com.kaffe.common.security;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtTokenServiceTest {

    private static final String SECRET = "kaffe-local-dev-secret-change-me-32chars";

    @Test
    void shouldGenerateAndParseJwtPrincipal() {
        JwtTokenService service = new JwtTokenService(
                SECRET,
                Clock.fixed(Instant.parse("2026-06-15T00:00:00Z"), ZoneOffset.UTC)
        );

        String token = service.generateToken(2L, "kevin@kaffe.com.mx", "owner", 86_400_000L);

        assertThat(service.parsePrincipal(token))
                .hasValue(new JwtPrincipal("kevin@kaffe.com.mx", 2L, "kevin@kaffe.com.mx", "owner"));
    }

    @Test
    void shouldRejectTokensSignedWithDifferentSecret() {
        JwtTokenService issuer = new JwtTokenService(SECRET);
        JwtTokenService verifier = new JwtTokenService("different-local-dev-secret-value-32chars");

        String token = issuer.generateToken(2L, "kevin@kaffe.com.mx", "owner", 86_400_000L);

        assertThat(verifier.parsePrincipal(token)).isEmpty();
    }

    @Test
    void shouldRejectPurposeTokenAsPrincipal() {
        JwtTokenService service = new JwtTokenService(SECRET);

        String token = service.generatePurposeToken(
                "kevin@kaffe.com.mx",
                "SIGNUP_VERIFICATION",
                java.util.Map.of("email", "kevin@kaffe.com.mx"),
                900_000L
        );

        assertThat(service.isValidPurposeToken(token, "kevin@kaffe.com.mx", "SIGNUP_VERIFICATION")).isTrue();
        assertThat(service.parsePrincipal(token)).isEmpty();
    }

    @Test
    void shouldRequireStrongEnoughSecret() {
        assertThatThrownBy(() -> new JwtTokenService("short"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least 32 characters");
    }
}
