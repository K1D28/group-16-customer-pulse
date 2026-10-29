package com.customerpulse.service;

import com.customerpulse.domain.AppUser;
import com.customerpulse.domain.Team;
import com.customerpulse.repository.TeamRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class TeamService {

    private final TeamRepository teams;
    private final AccessGuard guard;

    public TeamService(TeamRepository teams, AccessGuard guard) {
        this.teams = teams;
        this.guard = guard;
    }

    /** A Manager renames their own team. Team names are unique. */
    public Team rename(String newName, AppUser me) {
        guard.checkManager(me);
        String name = newName == null ? "" : newName.trim();
        if (name.isEmpty()) throw new BusinessException("Team name is required.");
        if (name.length() > 100) throw new BusinessException("Team name can be up to 100 characters.");
        Team team = teams.findById(me.getTeam().getId()).orElseThrow();
        teams.findByNameIgnoreCase(name)
                .filter(other -> !other.getId().equals(team.getId()))
                .ifPresent(other -> { throw new BusinessException("Another team is already called \"" + name + "\"."); });
        team.setName(name);
        return team;
    }
}
