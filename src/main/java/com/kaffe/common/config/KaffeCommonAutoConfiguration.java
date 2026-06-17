package com.kaffe.common.config;

import com.kaffe.common.exception.KaffeGlobalExceptionHandler;
import com.kaffe.common.security.CurrentUserProvider;
import com.kaffe.common.security.JwtAuthenticationFilter;
import com.kaffe.common.security.JwtTokenService;
import com.kaffe.common.web.RequestTraceFilter;
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

import javax.sql.DataSource;

@AutoConfiguration(before = DataSourceAutoConfiguration.class)
@EnableConfigurationProperties({KaffeDataSourceProperties.class, KaffeJwtProperties.class, KaffeCorsProperties.class})
public class KaffeCommonAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public JwtTokenService jwtTokenService(KaffeJwtProperties properties, Environment environment) {
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
        dataSource.setMaximumPoolSize(properties.getMaximumPoolSize());
        dataSource.setMinimumIdle(properties.getMinimumIdle());
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
