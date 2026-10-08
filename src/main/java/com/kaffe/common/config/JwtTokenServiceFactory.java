package com.kaffe.common.config;

import com.kaffe.common.security.JwtTokenService;
import org.springframework.core.env.Environment;

import java.time.Clock;
import java.time.Instant;

/** Resolves legacy aliases in one place without silently downgrading RSA deployments. */
final class JwtTokenServiceFactory {
    private JwtTokenServiceFactory() { }

    static JwtTokenService create(KaffeJwtProperties properties, Environment environment) {
        String publicKey = first(properties.getPublicKey(), environment.getProperty("JWT_PUBLIC_KEY_BASE64"),
                environment.getProperty("JWT_PUBLIC_KEY"));
        String privateKey = first(properties.getPrivateKey(), environment.getProperty("JWT_PRIVATE_KEY_BASE64"),
                environment.getProperty("JWT_PRIVATE_KEY"));
        if (privateKey != null && !properties.isSigningEnabled()) {
            throw new IllegalStateException("JWT private keys must not be supplied to verifier-only services");
        }
        if (publicKey != null) {
            if (properties.isRequireAsymmetric() && properties.isSigningEnabled() && privateKey == null) {
                throw new IllegalStateException("JWT asymmetric issuer requires a private signing key");
            }
            Instant deadline = null;
            if (properties.isAllowLegacyHmac()) {
                if (first(properties.getLegacyAcceptUntil()) == null) {
                    throw new IllegalStateException("JWT legacy verification requires an explicit UTC expiry");
                }
                deadline = Instant.parse(properties.getLegacyAcceptUntil());
            }
            return new JwtTokenService(publicKey, privateKey,
                    first(properties.getLegacySecret(), environment.getProperty("JWT_LEGACY_SECRET")),
                    properties.isAllowLegacyHmac(), properties.getIssuer(), properties.getKeyId(),
                    Clock.systemUTC(), properties.isSigningEnabled(), deadline);
        }
        if (properties.isRequireAsymmetric() || privateKey != null || properties.isAllowLegacyHmac()) {
            throw new IllegalStateException("JWT asymmetric verification requires a public key; HMAC fallback denied");
        }
        return new JwtTokenService(first(properties.getSecret(), environment.getProperty("jwt.secret"),
                environment.getProperty("auth.jwt.secret"), environment.getProperty("JWT_SECRET")),
                Clock.systemUTC(), properties.isSigningEnabled());
    }

    private static String first(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) return value;
        }
        return null;
    }
}
