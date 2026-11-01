package com.customerpulse.config;

import com.customerpulse.domain.AppUser;
import com.customerpulse.domain.Role;
import com.customerpulse.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * FR-10: on an empty database, creates the first Manager account. Every other account is created by a Manager.
 * FR-13: this installation step is not audited, because no user exists yet to record as the actor.
 */
@Component
@Order(1)
public class InitialSetup implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(InitialSetup.class);

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final String name;
    private final String email;
    private final String password;

    public InitialSetup(UserRepository users, PasswordEncoder encoder,
                        @Value("${app.first-manager.name:Priya Raman}") String name,
                        @Value("${app.first-manager.email:priya@example.com}") String email,
                        @Value("${app.first-manager.password:password123}") String password) {
        this.users = users;
        this.encoder = encoder;
        this.name = name;
        this.email = email;
        this.password = password;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (users.count() > 0) return;
        AppUser u = new AppUser();
        u.setFullName(name);
        u.setEmail(email.trim().toLowerCase());
        u.setRole(Role.MANAGER);
        u.setPasswordHash(encoder.encode(password));
        users.save(u);
        log.info("Created the first Manager account: {}", u.getEmail());
    }
}
