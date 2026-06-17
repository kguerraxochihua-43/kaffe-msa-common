package com.kaffe.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "kaffe.cors")
public class KaffeCorsProperties {

    private boolean enabled = true;

    private List<String> allowedOrigins = new ArrayList<>(List.of(
            "http://localhost:4200",
            "http://localhost:4210",
            "http://localhost:4220",
            "http://localhost:4230",
            "http://127.0.0.1:4200",
            "http://127.0.0.1:4210",
            "http://127.0.0.1:4220",
            "http://127.0.0.1:4230"
    ));

    private List<String> allowedOriginPatterns = new ArrayList<>(List.of(
            "http://localhost:[*]",
            "http://127.0.0.1:[*]",
            "https://*.kaffe.mx"
    ));

    private List<String> allowedMethods = new ArrayList<>(List.of(
            "GET",
            "POST",
            "PUT",
            "PATCH",
            "DELETE",
            "OPTIONS"
    ));

    private List<String> allowedHeaders = new ArrayList<>(List.of(
            "Authorization",
            "Content-Type",
            "Accept",
            "X-Requested-With",
            "X-Trace-Id"
    ));

    private List<String> exposedHeaders = new ArrayList<>(List.of(
            "Authorization",
            "X-Trace-Id"
    ));

    private boolean allowCredentials = true;

    private Long maxAge = 3600L;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public List<String> getAllowedOrigins() {
        return allowedOrigins;
    }

    public void setAllowedOrigins(List<String> allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }

    public List<String> getAllowedOriginPatterns() {
        return allowedOriginPatterns;
    }

    public void setAllowedOriginPatterns(List<String> allowedOriginPatterns) {
        this.allowedOriginPatterns = allowedOriginPatterns;
    }

    public List<String> getAllowedMethods() {
        return allowedMethods;
    }

    public void setAllowedMethods(List<String> allowedMethods) {
        this.allowedMethods = allowedMethods;
    }

    public List<String> getAllowedHeaders() {
        return allowedHeaders;
    }

    public void setAllowedHeaders(List<String> allowedHeaders) {
        this.allowedHeaders = allowedHeaders;
    }

    public List<String> getExposedHeaders() {
        return exposedHeaders;
    }

    public void setExposedHeaders(List<String> exposedHeaders) {
        this.exposedHeaders = exposedHeaders;
    }

    public boolean isAllowCredentials() {
        return allowCredentials;
    }

    public void setAllowCredentials(boolean allowCredentials) {
        this.allowCredentials = allowCredentials;
    }

    public Long getMaxAge() {
        return maxAge;
    }

    public void setMaxAge(Long maxAge) {
        this.maxAge = maxAge;
    }
}
