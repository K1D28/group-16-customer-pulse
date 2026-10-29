package com.customerpulse.service;

import com.customerpulse.domain.AppUser;
import com.customerpulse.repository.*;
import com.customerpulse.web.form.UserForm;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class UserService {

    public static final int MIN_PASSWORD = 8;

    private final UserRepository users;
    private final CustomerRepository customers;
    private final ActivityRepository activities;
    private final FollowUpRepository followUps;
    private final StatusHistoryRepository history;
    private final CaseRepository cases;
    private final CaseAssignmentRepository caseAssignments;
    private final PasswordEncoder encoder;

    public UserService(UserRepository users, CustomerRepository customers, ActivityRepository activities,
                       FollowUpRepository followUps, StatusHistoryRepository history, CaseRepository cases,
                       CaseAssignmentRepository caseAssignments, PasswordEncoder encoder) {
        this.users = users;
        this.customers = customers;
        this.activities = activities;
        this.followUps = followUps;
        this.history = history;
        this.cases = cases;
        this.caseAssignments = caseAssignments;
        this.encoder = encoder;
    }

    /** Everyone in the user's team. */
    @Transactional(readOnly = true)
    public List<AppUser> team(AppUser me) {
        return users.findByTeamIdOrderByFullNameAsc(me.getTeam().getId());
    }

    /** Active people in the team: the only ones who can be given customers, cases or follow-ups. */
    @Transactional(readOnly = true)
    public List<AppUser> activeTeam(AppUser me) {
        return users.findByTeamIdAndActiveTrueOrderByFullNameAsc(me.getTeam().getId());
    }

    /**
     * Deactivate (someone leaving) or reactivate a user. A deactivated user cannot log in.
     * They must not own customers any more: reassign those first (FR-09).
     */
    public AppUser setActive(Long id, boolean active, AppUser me) {
        AppUser u = get(id, me);
        if (u.getId().equals(me.getId())) throw new BusinessException("You cannot deactivate your own account.");
        if (!active) {
            long owned = customers.countByOwnerId(id);
            if (owned > 0) {
                throw new BusinessException(u.getFullName() + " still owns " + owned
                        + " customer(s). Reassign them to an active user first.");
            }
        }
        u.setActive(active);
        return u;
    }

    /** A user in the same team as me; people in other teams look like they do not exist. */
    @Transactional(readOnly = true)
    public AppUser get(Long id, AppUser me) {
        return users.findById(id).filter(u -> u.sameTeam(me))
                .orElseThrow(() -> new NotFoundException("User not found"));
    }

    @Transactional(readOnly = true)
    public boolean emailTaken(String email, Long exceptId) {
        return users.findByEmailIgnoreCase(email.trim())
                .map(u -> !u.getId().equals(exceptId))
                .orElse(false);
    }

    /** FR-10: email must be unique; password is stored hashed. New users join the manager's team. */
    public AppUser create(UserForm form, AppUser me) {
        if (emailTaken(form.getEmail(), null)) throw new BusinessException("That email is already in use.");
        AppUser u = new AppUser();
        apply(u, form);
        u.setTeam(me.getTeam());
        u.setPasswordHash(encoder.encode(form.getPassword()));
        return users.save(u);
    }

    public AppUser update(Long id, UserForm form, AppUser me) {
        AppUser u = get(id, me);
        if (emailTaken(form.getEmail(), id)) throw new BusinessException("That email is already in use.");
        if (u.getId().equals(me.getId()) && !form.getRole().equals(u.getRole())) {
            throw new BusinessException("You cannot change your own role.");
        }
        apply(u, form);
        if (form.getPassword() != null && !form.getPassword().isBlank()) {
            u.setPasswordHash(encoder.encode(form.getPassword()));
        }
        return u;
    }

    /** FR-10 step 5: a user who still owns customers cannot be deleted. */
    public void delete(Long id, AppUser me) {
        AppUser u = get(id, me);
        if (u.getId().equals(me.getId())) throw new BusinessException("You cannot delete your own account.");
        long owned = customers.countByOwnerId(id);
        if (owned > 0) {
            throw new BusinessException(u.getFullName() + " still owns " + owned
                    + " customer(s). Reassign them first.");
        }
        long logged = activities.countByUserId(id) + history.countByChangedById(id) + followUps.countByAssignedUserId(id)
                + cases.countByCreatedById(id) + caseAssignments.countByUserId(id);
        if (logged > 0) {
            throw new BusinessException(u.getFullName() + " has " + logged
                    + " activity, follow-up, case or status records. They are kept for the audit trail, so this user cannot be deleted.");
        }
        users.delete(u);
    }

    private void apply(AppUser u, UserForm f) {
        u.setFullName(f.getFullName().trim());
        u.setEmail(f.getEmail().trim().toLowerCase());
        u.setRole(f.getRole());
    }
}
