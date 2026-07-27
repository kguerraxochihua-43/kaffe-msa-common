package com.kaffe.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "kaffe.jwt")
public class KaffeJwtProperties {

    private String secret;
    private String publicKey;
    private String privateKey;
    private String legacySecret;
    private boolean allowLegacyHmac;
    private String issuer = "kaffe";
    private String keyId = "kaffe-auth-rs256-v1";
    private String cookieName = "KAFFE_AUTH";

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public String getPublicKey() {
        return publicKey;
    }

    public void setPublicKey(String publicKey) {
        this.publicKey = publicKey;
    }

    public String getPrivateKey() {
        return privateKey;
    }

    public void setPrivateKey(String privateKey) {
        this.privateKey = privateKey;
    }

    public String getLegacySecret() {
        return legacySecret;
    }

    public void setLegacySecret(String legacySecret) {
        this.legacySecret = legacySecret;
    }

    public boolean isAllowLegacyHmac() {
        return allowLegacyHmac;
    }

    public void setAllowLegacyHmac(boolean allowLegacyHmac) {
        this.allowLegacyHmac = allowLegacyHmac;
    }

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public String getKeyId() {
        return keyId;
    }

    public void setKeyId(String keyId) {
        this.keyId = keyId;
    }

    public String getCookieName() {
        return cookieName;
    }

    public void setCookieName(String cookieName) {
        this.cookieName = cookieName;
    }
}
