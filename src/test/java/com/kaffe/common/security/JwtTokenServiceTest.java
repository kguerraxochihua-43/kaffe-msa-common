package com.kaffe.common.security;

import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;

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

    @Test
    void shouldIssueRs256AndVerifyWithPublicKeyOnly() throws Exception {
        KeyPair keyPair = rsaKeyPair();
        Clock clock = Clock.fixed(Instant.parse("2026-06-15T00:00:00Z"), ZoneOffset.UTC);
        JwtTokenService issuer = new JwtTokenService(
                encoded(keyPair.getPublic()),
                encoded(keyPair.getPrivate()),
                null,
                false,
                "kaffe",
                "kaffe-auth-rs256-v1",
                clock
        );
        JwtTokenService verifier = new JwtTokenService(
                encoded(keyPair.getPublic()),
                null,
                null,
                false,
                "kaffe",
                "kaffe-auth-rs256-v1",
                clock
        );

        String token = issuer.generateToken(2L, "kevin@kaffe.com.mx", "owner", 86_400_000L);

        assertThat(verifier.parsePrincipal(token))
                .hasValue(new JwtPrincipal("kevin@kaffe.com.mx", 2L, "kevin@kaffe.com.mx", "owner"));
        assertThatThrownBy(() -> verifier.generateToken(2L, "kevin@kaffe.com.mx", "owner", 86_400_000L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("private key");
    }

    @Test
    void shouldAcceptLegacyHmacOnlyDuringMigrationWindow() throws Exception {
        KeyPair keyPair = rsaKeyPair();
        JwtTokenService legacyIssuer = new JwtTokenService(SECRET);
        JwtTokenService transitionalVerifier = new JwtTokenService(
                encoded(keyPair.getPublic()),
                null,
                SECRET,
                true,
                "kaffe",
                "kaffe-auth-rs256-v1"
        );
        JwtTokenService strictVerifier = new JwtTokenService(
                encoded(keyPair.getPublic()),
                null,
                null,
                false,
                "kaffe",
                "kaffe-auth-rs256-v1"
        );
        String token = legacyIssuer.generateToken(2L, "kevin@kaffe.com.mx", "owner", 86_400_000L);

        assertThat(transitionalVerifier.parsePrincipal(token)).isPresent();
        assertThat(strictVerifier.parsePrincipal(token)).isEmpty();
    }

    @Test
    void shouldRejectWeakRsaKeys() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(1024);
        KeyPair weakKeyPair = generator.generateKeyPair();

        assertThatThrownBy(() -> new JwtTokenService(
                encoded(weakKeyPair.getPublic()),
                encoded(weakKeyPair.getPrivate()),
                null,
                false,
                "kaffe",
                "kaffe-auth-rs256-v1"
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least 2048 bits");
    }

    private static KeyPair rsaKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }

    private static String encoded(java.security.Key key) {
        return Base64.getEncoder().encodeToString(key.getEncoded());
    }
}
