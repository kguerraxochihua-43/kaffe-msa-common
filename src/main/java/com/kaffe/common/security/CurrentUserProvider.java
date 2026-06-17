package com.kaffe.common.security;

import com.kaffe.common.exception.UnauthorizedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

public class CurrentUserProvider {

    public Optional<CurrentUser> currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.empty();
        }

        if (authentication.getDetails() instanceof KaffeAuthenticationDetails details
                && details.principal() != null) {
            JwtPrincipal principal = details.principal();
            return Optional.of(new CurrentUser(
                    principal.subject(),
                    principal.userId(),
                    principal.email(),
                    principal.role(),
                    true
            ));
        }

        String name = authentication.getName();
        if (name == null || name.isBlank() || "anonymousUser".equals(name)) {
            return Optional.empty();
        }

        return Optional.of(new CurrentUser(name, null, name, null, true));
    }

    public CurrentUser requireUser() {
        return currentUser().orElseThrow(() -> new UnauthorizedException("Authentication is required"));
    }

    public String requireEmail() {
        CurrentUser currentUser = requireUser();
        String email = currentUser.email() != null ? currentUser.email() : currentUser.subject();
        if (email == null || email.isBlank()) {
            throw new UnauthorizedException("Authenticated user email is required");
        }
        return email;
    }

    public Long requireUserId() {
        Long userId = requireUser().userId();
        if (userId == null) {
            throw new UnauthorizedException("Authenticated user id is required");
        }
        return userId;
    }
}
