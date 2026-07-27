package com.kaffe.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParserBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Map;
import java.util.Optional;

public class JwtTokenService {

    public static final String CLAIM_TOKEN_TYPE = "tokenType";
    public static final String TOKEN_TYPE_ACCESS = "ACCESS";
    public static final String TOKEN_TYPE_PURPOSE = "PURPOSE";
    public static final String CLAIM_PURPOSE = "purpose";

    private final RSAPrivateKey privateKey;
    private final RSAPublicKey publicKey;
    private final SecretKey legacyHmacKey;
    private final boolean allowLegacyHmac;
    private final String issuer;
    private final String keyId;
    private final Clock clock;

    public JwtTokenService(String secret) {
        this(secret, Clock.systemUTC());
    }

    public JwtTokenService(String secret, Clock clock) {
        if (secret == null || secret.length() < 32) {
            throw new IllegalArgumentException("JWT secret must contain at least 32 characters for HS256");
        }
        this.privateKey = null;
        this.publicKey = null;
        this.legacyHmacKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.allowLegacyHmac = true;
        this.issuer = null;
        this.keyId = null;
        this.clock = clock;
    }

    public JwtTokenService(
            String publicKey,
            String privateKey,
            String legacySecret,
            boolean allowLegacyHmac,
            String issuer,
            String keyId
    ) {
        this(publicKey, privateKey, legacySecret, allowLegacyHmac, issuer, keyId, Clock.systemUTC());
    }

    JwtTokenService(
            String publicKey,
            String privateKey,
            String legacySecret,
            boolean allowLegacyHmac,
            String issuer,
            String keyId,
            Clock clock
    ) {
        this.publicKey = readPublicKey(publicKey);
        this.privateKey = isBlank(privateKey) ? null : readPrivateKey(privateKey);
        this.allowLegacyHmac = allowLegacyHmac;
        this.legacyHmacKey = allowLegacyHmac ? readLegacyKey(legacySecret) : null;
        this.issuer = requireNonBlank(issuer, "JWT issuer is required for RS256");
        this.keyId = requireNonBlank(keyId, "JWT key id is required for RS256");
        this.clock = clock;
    }

    public String generateToken(Long userId, String email, String role, long expirationMs) {
        return generateToken(
                email,
                Map.of(
                        "userId", String.valueOf(userId),
                        "email", email,
                        "role", role,
                        CLAIM_TOKEN_TYPE, TOKEN_TYPE_ACCESS
                ),
                expirationMs
        );
    }

    public String generatePurposeToken(String subject, String purpose, Map<String, ?> claims, long expirationMs) {
        java.util.LinkedHashMap<String, Object> tokenClaims = new java.util.LinkedHashMap<>(claims);
        tokenClaims.put(CLAIM_TOKEN_TYPE, TOKEN_TYPE_PURPOSE);
        tokenClaims.put(CLAIM_PURPOSE, purpose);
        return generateToken(subject, tokenClaims, expirationMs);
    }

    public String generateToken(String subject, Map<String, ?> claims, long expirationMs) {
        Instant now = clock.instant();
        var builder = Jwts.builder()
                .subject(subject)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(expirationMs)));
        if (privateKey != null) {
            builder.issuer(issuer)
                    .header()
                    .keyId(keyId)
                    .and()
                    .signWith(privateKey, Jwts.SIG.RS256);
        } else if (legacyHmacKey != null) {
            builder.signWith(legacyHmacKey);
        } else {
            throw new IllegalStateException("JWT signing is only available to services configured with a private key");
        }
        claims.forEach(builder::claim);
        return builder.compact();
    }

    public Optional<JwtPrincipal> parsePrincipal(String token) {
        try {
            Claims claims = parseClaims(token);
            String subject = claims.getSubject();
            String email = claims.get("email", String.class);
            String role = claims.get("role", String.class);
            String tokenType = claims.get(CLAIM_TOKEN_TYPE, String.class);
            Long userId = parseUserId(claims.get("userId", String.class));

            if (isBlank(subject)
                    || isBlank(email)
                    || isBlank(role)
                    || userId == null
                    || !TOKEN_TYPE_ACCESS.equals(tokenType)) {
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
            String purpose = claims.get(CLAIM_PURPOSE, String.class);
            String tokenType = claims.get(CLAIM_TOKEN_TYPE, String.class);
            return expectedSubject != null
                    && expectedSubject.equalsIgnoreCase(subject)
                    && expectedPurpose.equals(purpose)
                    && TOKEN_TYPE_PURPOSE.equals(tokenType);
        } catch (JwtException | IllegalArgumentException ex) {
            return false;
        }
    }

    private Claims parseClaims(String token) {
        JwtException asymmetricFailure = null;
        if (publicKey != null) {
            try {
                return parser()
                        .verifyWith(publicKey)
                        .requireIssuer(issuer)
                        .build()
                        .parseSignedClaims(token)
                        .getPayload();
            } catch (JwtException ex) {
                asymmetricFailure = ex;
            }
        }
        if (allowLegacyHmac && legacyHmacKey != null) {
            return parser()
                    .verifyWith(legacyHmacKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        }
        if (asymmetricFailure != null) {
            throw asymmetricFailure;
        }
        throw new IllegalStateException("JWT verification key is not configured");
    }

    private JwtParserBuilder parser() {
        return Jwts.parser().clock(() -> Date.from(clock.instant()));
    }

    private static RSAPublicKey readPublicKey(String value) {
        try {
            PublicKey key = KeyFactory.getInstance("RSA")
                    .generatePublic(new X509EncodedKeySpec(decodeKey(value)));
            if (key instanceof RSAPublicKey rsaPublicKey && rsaPublicKey.getModulus().bitLength() >= 2048) {
                return rsaPublicKey;
            }
            throw new IllegalArgumentException("JWT RSA public key must contain at least 2048 bits");
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalArgumentException("JWT RSA public key is invalid", ex);
        }
    }

    private static RSAPrivateKey readPrivateKey(String value) {
        try {
            PrivateKey key = KeyFactory.getInstance("RSA")
                    .generatePrivate(new PKCS8EncodedKeySpec(decodeKey(value)));
            if (key instanceof RSAPrivateKey rsaPrivateKey && rsaPrivateKey.getModulus().bitLength() >= 2048) {
                return rsaPrivateKey;
            }
            throw new IllegalArgumentException("JWT RSA private key must contain at least 2048 bits");
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalArgumentException("JWT RSA private key is invalid", ex);
        }
    }

    private static byte[] decodeKey(String value) {
        if (isBlank(value)) {
            throw new IllegalArgumentException("JWT RSA key is required");
        }
        String normalized = value
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        return Base64.getDecoder().decode(normalized);
    }

    private static SecretKey readLegacyKey(String secret) {
        if (isBlank(secret) || secret.length() < 32) {
            throw new IllegalArgumentException(
                    "JWT legacy secret must contain at least 32 characters while legacy verification is enabled"
            );
        }
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    private static String requireNonBlank(String value, String message) {
        if (isBlank(value)) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }

    private Long parseUserId(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return Long.valueOf(value);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
