package com.kaffe.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenService jwtTokenService;
    private final String jwtCookieName;

    public JwtAuthenticationFilter(JwtTokenService jwtTokenService, String jwtCookieName) {
        this.jwtTokenService = jwtTokenService;
        this.jwtCookieName = jwtCookieName;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        Optional<String> token = BearerTokenResolver.resolve(request, jwtCookieName);

        if (token.isPresent() && SecurityContextHolder.getContext().getAuthentication() == null) {
            jwtTokenService.parsePrincipal(token.get()).ifPresent(principal -> {
                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                        principal.subject(),
                        null,
                        authorities(principal.role())
                );
                authentication.setDetails(new KaffeAuthenticationDetails(
                        principal,
                        new WebAuthenticationDetailsSource().buildDetails(request)
                ));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            });
        }

        filterChain.doFilter(request, response);
    }

    private List<SimpleGrantedAuthority> authorities(String role) {
        if (role == null || role.isBlank()) {
            return Collections.emptyList();
        }
        return List.of(new SimpleGrantedAuthority("ROLE_" + role));
    }
}
