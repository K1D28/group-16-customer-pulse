package com.customerpulse.config;

import com.customerpulse.domain.AppUser;
import com.customerpulse.domain.Role;
import com.customerpulse.domain.Team;
import com.customerpulse.repository.TeamRepository;
import com.customerpulse.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * On an empty database, creates 3 teams with one Manager each. Nothing else:
 * managers add their own staff, customers and cases.
 */
@Component
@Order(1)
public class TeamSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(TeamSeeder.class);
    public static final String START_PASSWORD = "password123";

    private final TeamRepository teams;
    private final UserRepository users;
    private final PasswordEncoder encoder;

    public TeamSeeder(TeamRepository teams, UserRepository users, PasswordEncoder encoder) {
        this.teams = teams;
        this.users = users;
        this.encoder = encoder;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (users.count() > 0) return;
        manager(team("Team A"), "Priya Raman", "priya@example.com");
        manager(team("Team B"), "Daniel Kim", "daniel@example.com");
        manager(team("Team C"), "Sofia Alvarez", "sofia@example.com");
        log.info("Created 3 teams. Managers: priya@example.com, daniel@example.com, sofia@example.com (password: {})", START_PASSWORD);
    }

    private Team team(String name) {
        Team t = new Team();
        t.setName(name);
        return teams.save(t);
    }

    private void manager(Team team, String name, String email) {
        AppUser u = new AppUser();
        u.setFullName(name);
        u.setEmail(email);
        u.setRole(Role.MANAGER);
        u.setTeam(team);
        u.setPasswordHash(encoder.encode(START_PASSWORD));
        users.save(u);
    }
}
