package com.customerpulse.security;

import com.customerpulse.domain.AppUser;
import com.customerpulse.repository.UserRepository;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Optional;

/** Looks up the logged-in user. */
@Component
public class CurrentUser {

    private final UserRepository users;

    public CurrentUser(UserRepository users) {
        this.users = users;
    }

    public Optional<AppUser> find() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            return Optional.empty();
        }
        return users.findByEmailIgnoreCase(auth.getName());
    }

    public AppUser get() {
        return find().orElseThrow(() -> new IllegalStateException("No logged-in user"));
    }
}
