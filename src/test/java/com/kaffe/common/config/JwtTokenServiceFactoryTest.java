package com.kaffe.common.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import java.security.KeyPairGenerator;
import java.util.Base64;
import static org.assertj.core.api.Assertions.*;

class JwtTokenServiceFactoryTest {
    @Test
    void asymmetricDeploymentCannotFallBackToHmac() {
        var properties = new KaffeJwtProperties();
        properties.setRequireAsymmetric(true);
        assertThatThrownBy(() -> JwtTokenServiceFactory.create(properties,
                new MockEnvironment().withProperty("JWT_SECRET", "fixture-hmac-key-not-a-real-secret")))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("fallback denied");
    }

    @Test
    void legacyRsaVerificationRequiresExplicitExpiry() throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        var properties = new KaffeJwtProperties();
        properties.setPublicKey(Base64.getEncoder().encodeToString(generator.generateKeyPair().getPublic().getEncoded()));
        properties.setAllowLegacyHmac(true);
        assertThatThrownBy(() -> JwtTokenServiceFactory.create(properties, new MockEnvironment()))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("explicit UTC expiry");
    }

    @Test
    void verifierCannotReceiveAPrivateKey() {
        var properties = new KaffeJwtProperties();
        properties.setSigningEnabled(false);
        assertThatThrownBy(() -> JwtTokenServiceFactory.create(properties,
                new MockEnvironment().withProperty("JWT_PRIVATE_KEY_BASE64", "fixture")))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("verifier-only");
    }

    @Test
    void legacyVerifierCannotUseTheSigningApi() {
        var properties = new KaffeJwtProperties();
        properties.setSigningEnabled(false);
        var service = JwtTokenServiceFactory.create(properties,
                new MockEnvironment().withProperty("JWT_SECRET", "fixture-hmac-key-not-a-real-secret"));
        assertThatThrownBy(() -> service.generateToken(1L, "test@example.test", "owner", 900000))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("disabled");
    }
}
