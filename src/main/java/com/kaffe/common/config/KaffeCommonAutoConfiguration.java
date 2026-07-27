package com.kaffe.common.config;

import com.kaffe.common.exception.KaffeGlobalExceptionHandler;
import com.kaffe.common.media.MediaAssetService;
import com.kaffe.common.security.CurrentUserProvider;
import com.kaffe.common.security.JwtAuthenticationFilter;
import com.kaffe.common.security.JwtTokenService;
import com.kaffe.common.web.RequestTraceFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.core.Ordered;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import javax.sql.DataSource;

@AutoConfiguration(before = DataSourceAutoConfiguration.class)
@EnableConfigurationProperties({
        KaffeDataSourceProperties.class,
        KaffeJwtProperties.class,
        KaffeCorsProperties.class,
        KaffeMediaProperties.class
})
public class KaffeCommonAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public JwtTokenService jwtTokenService(KaffeJwtProperties properties, Environment environment) {
        String publicKey = firstNonBlank(
                properties.getPublicKey(),
                environment.getProperty("JWT_PUBLIC_KEY_BASE64"),
                environment.getProperty("JWT_PUBLIC_KEY")
        );
        if (publicKey != null) {
            return new JwtTokenService(
                    publicKey,
                    firstNonBlank(
                            properties.getPrivateKey(),
                            environment.getProperty("JWT_PRIVATE_KEY_BASE64"),
                            environment.getProperty("JWT_PRIVATE_KEY")
                    ),
                    firstNonBlank(
                            properties.getLegacySecret(),
                            environment.getProperty("JWT_LEGACY_SECRET")
                    ),
                    properties.isAllowLegacyHmac(),
                    properties.getIssuer(),
                    properties.getKeyId()
            );
        }

        return new JwtTokenService(firstNonBlank(
                properties.getSecret(),
                environment.getProperty("jwt.secret"),
                environment.getProperty("auth.jwt.secret"),
                environment.getProperty("JWT_SECRET")
        ));
    }

    @Bean
    @ConditionalOnMissingBean
    public JwtAuthenticationFilter jwtAuthenticationFilter(
            JwtTokenService jwtTokenService,
            KaffeJwtProperties properties,
            Environment environment
    ) {
        String cookieName = firstNonBlank(
                properties.getCookieName(),
                environment.getProperty("jwt.cookie-name"),
                environment.getProperty("auth.jwt-cookie.name"),
                environment.getProperty("JWT_COOKIE_NAME"),
                "KAFFE_AUTH"
        );
        return new JwtAuthenticationFilter(jwtTokenService, cookieName);
    }

    @Bean
    @ConditionalOnMissingBean
    public RequestTraceFilter requestTraceFilter() {
        return new RequestTraceFilter();
    }

    @Bean
    @Primary
    @ConditionalOnProperty(prefix = "kaffe.cors", name = "enabled", havingValue = "true", matchIfMissing = true)
    public CorsConfigurationSource corsConfigurationSource(KaffeCorsProperties properties) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(properties.getAllowedOrigins());
        configuration.setAllowedOriginPatterns(properties.getAllowedOriginPatterns());
        configuration.setAllowedMethods(properties.getAllowedMethods());
        configuration.setAllowedHeaders(properties.getAllowedHeaders());
        configuration.setExposedHeaders(properties.getExposedHeaders());
        configuration.setAllowCredentials(properties.isAllowCredentials());
        configuration.setMaxAge(properties.getMaxAge());

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    @ConditionalOnBean(CorsConfigurationSource.class)
    @ConditionalOnMissingBean(name = "kaffeCorsFilterRegistration")
    @ConditionalOnProperty(prefix = "kaffe.cors", name = "enabled", havingValue = "true", matchIfMissing = true)
    public FilterRegistrationBean<CorsFilter> kaffeCorsFilterRegistration(
            @Qualifier("corsConfigurationSource") CorsConfigurationSource source
    ) {
        FilterRegistrationBean<CorsFilter> registration = new FilterRegistrationBean<>(new CorsFilter(source));
        registration.setName("kaffeCorsFilter");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 1);
        registration.addUrlPatterns("/*");
        return registration;
    }

    @Bean
    @ConditionalOnBean(RequestTraceFilter.class)
    @ConditionalOnMissingBean(name = "requestTraceFilterRegistration")
    public FilterRegistrationBean<RequestTraceFilter> requestTraceFilterRegistration(RequestTraceFilter filter) {
        FilterRegistrationBean<RequestTraceFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setName("requestTraceFilter");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        registration.addUrlPatterns("/*");
        return registration;
    }

    @Bean
    @ConditionalOnMissingBean
    public CurrentUserProvider currentUserProvider() {
        return new CurrentUserProvider();
    }

    @Bean
    @ConditionalOnMissingBean
    public KaffeGlobalExceptionHandler kaffeGlobalExceptionHandler() {
        return new KaffeGlobalExceptionHandler();
    }

    @Bean
    @ConditionalOnClass(S3Presigner.class)
    @ConditionalOnMissingBean
    public S3Presigner s3Presigner(KaffeMediaProperties properties, Environment environment) {
        return S3Presigner.builder()
                .region(Region.of(firstNonBlank(
                        properties.getS3().getRegion(),
                        environment.getProperty("AWS_REGION"),
                        environment.getProperty("AWS_DEFAULT_REGION"),
                        "us-east-1"
                )))
                .build();
    }

    @Bean
    @ConditionalOnClass(S3Client.class)
    @ConditionalOnMissingBean
    public S3Client s3Client(KaffeMediaProperties properties, Environment environment) {
        return S3Client.builder()
                .region(Region.of(firstNonBlank(
                        properties.getS3().getRegion(),
                        environment.getProperty("AWS_REGION"),
                        environment.getProperty("AWS_DEFAULT_REGION"),
                        "us-east-1"
                )))
                .build();
    }

    @Bean
    @ConditionalOnMissingBean
    public MediaAssetService mediaAssetService(
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            KaffeMediaProperties properties,
            S3Presigner s3Presigner,
            S3Client s3Client
    ) {
        return new MediaAssetService(jdbcTemplate, objectMapper, properties, s3Presigner, s3Client);
    }

    @Bean
    @ConditionalOnClass(HikariDataSource.class)
    @ConditionalOnMissingBean(DataSource.class)
    @ConditionalOnProperty(prefix = "kaffe.datasource", name = "enabled", havingValue = "true", matchIfMissing = true)
    public DataSource dataSource(KaffeDataSourceProperties properties, Environment environment) {
        String url = firstNonBlank(
                properties.getUrl(),
                environment.getProperty("spring.datasource.url"),
                environment.getProperty("DB_URL"),
                environment.getProperty("DATABASE_URL")
        );
        if (url == null || url.isBlank()) {
            throw new IllegalStateException("Database URL is required. Configure kaffe.datasource.url or spring.datasource.url");
        }

        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setJdbcUrl(normalizeJdbcUrl(url));
        dataSource.setUsername(firstNonBlank(
                properties.getUsername(),
                environment.getProperty("spring.datasource.username"),
                environment.getProperty("DB_USERNAME"),
                environment.getProperty("DB_USER")
        ));
        dataSource.setPassword(firstNonBlank(
                properties.getPassword(),
                environment.getProperty("spring.datasource.password"),
                environment.getProperty("DB_PASSWORD")
        ));
        dataSource.setDriverClassName(firstNonBlank(
                properties.getDriverClassName(),
                environment.getProperty("spring.datasource.driver-class-name"),
                "org.postgresql.Driver"
        ));
        int maximumPoolSize = Math.max(1, properties.getMaximumPoolSize());
        int minimumIdle = Math.max(0, Math.min(properties.getMinimumIdle(), maximumPoolSize));
        dataSource.setMaximumPoolSize(maximumPoolSize);
        dataSource.setMinimumIdle(minimumIdle);
        dataSource.setConnectionTimeout(properties.getConnectionTimeout());
        return dataSource;
    }

    private static String normalizeJdbcUrl(String url) {
        if (url.startsWith("postgresql://")) {
            return "jdbc:" + url;
        }
        return url;
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "kaffe.datasource", name = "enabled", havingValue = "true", matchIfMissing = true)
    public JdbcTemplate jdbcTemplate(DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }
}
