package com.customerpulse.service;

import com.customerpulse.domain.AppUser;
import com.customerpulse.domain.AuditRecordType;
import com.customerpulse.domain.Role;
import com.customerpulse.repository.CustomerRepository;
import com.customerpulse.repository.UserRepository;
import com.customerpulse.web.form.UserForm;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/** FR-10: Managers create, update, deactivate and reactivate users. Users are never deleted. */
@Service
@Transactional
public class UserService {

    public static final int MIN_PASSWORD = 8;

    private final UserRepository users;
    private final CustomerRepository customers;
    private final PasswordEncoder encoder;
    private final AccessGuard guard;
    private final AuditService audit;

    public UserService(UserRepository users, CustomerRepository customers, PasswordEncoder encoder,
                       AccessGuard guard, AuditService audit) {
        this.users = users;
        this.customers = customers;
        this.encoder = encoder;
        this.guard = guard;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<AppUser> all() {
        return users.findAllByOrderByFullNameAsc();
    }

    /** Active users: the only ones who can be chosen as owners. */
    @Transactional(readOnly = true)
    public List<AppUser> active() {
        return users.findByActiveTrueOrderByFullNameAsc();
    }

    @Transactional(readOnly = true)
    public AppUser get(Long id) {
        return users.findById(id).orElseThrow(() -> new NotFoundException("User not found"));
    }

    @Transactional(readOnly = true)
    public boolean emailTaken(String email, Long exceptId) {
        return users.findByEmailIgnoreCase(email.trim())
                .map(u -> !u.getId().equals(exceptId))
                .orElse(false);
    }

    public AppUser create(UserForm form, AppUser me) {
        guard.checkManager(me);
        if (emailTaken(form.getEmail(), null)) throw new BusinessException("That email is already in use.");
        checkPassword(form.getPassword(), true);
        AppUser u = new AppUser();
        apply(u, form);
        u.setPasswordHash(encoder.encode(form.getPassword()));
        users.save(u);
        audit.created(me, AuditRecordType.USER, u.getId(), null, AuditService.snapshot(u));
        return u;
    }

    /** Name, email, role and an optional password reset. A reset is audited only as "password changed". */
    public AppUser update(Long id, UserForm form, AppUser me) {
        guard.checkManager(me);
        AppUser u = get(id);
        if (emailTaken(form.getEmail(), id)) throw new BusinessException("That email is already in use.");
        if (u.getId().equals(me.getId()) && form.getRole() != u.getRole()) {
            throw new BusinessException("You cannot change your own role.");
        }
        if (u.isManager() && u.isActive() && form.getRole() != Role.MANAGER && isLastActiveManager()) {
            throw new BusinessException("There must always be at least one active Manager.");
        }
        boolean reset = form.getPassword() != null && !form.getPassword().isBlank();
        if (reset) checkPassword(form.getPassword(), true);

        Map<String, Object> before = AuditService.snapshot(u);
        apply(u, form);
        Map<String, Object> diff = AuditService.diff(before, AuditService.snapshot(u));
        if (reset) {
            u.setPasswordHash(encoder.encode(form.getPassword()));
            diff.put("password", AuditService.PASSWORD_CHANGED);
        }
        audit.updatedFields(me, AuditRecordType.USER, u.getId(), null, diff);
        return u;
    }

    /**
     * Deactivate (cannot log in, cannot own customers) or reactivate. Refused for yourself, for a user
     * who still owns customers, and for the last active Manager.
     */
    public AppUser setActive(Long id, boolean active, AppUser me) {
        guard.checkManager(me);
        AppUser u = get(id);
        if (u.isActive() == active) return u;
        if (!active) {
            if (u.getId().equals(me.getId())) throw new BusinessException("You cannot deactivate your own account.");
            long owned = customers.countByOwnerIdAndDeletedAtIsNull(id);
            if (owned > 0) {
                throw new BusinessException(u.getFullName() + " still owns " + owned
                        + " customer(s). Reassign them to an active user first.");
            }
            if (u.isManager() && isLastActiveManager()) {
                throw new BusinessException("There must always be at least one active Manager.");
            }
        }
        Map<String, Object> before = AuditService.snapshot(u);
        u.setActive(active);
        audit.updated(me, AuditRecordType.USER, u.getId(), null, before, AuditService.snapshot(u));
        return u;
    }

    private boolean isLastActiveManager() {
        return users.countByRoleAndActiveTrue(Role.MANAGER) <= 1;
    }

    private static void checkPassword(String p, boolean required) {
        if (p == null || p.isBlank()) {
            if (required) throw new BusinessException("A password is required.");
        } else if (p.length() < MIN_PASSWORD) {
            throw new BusinessException("Passwords must be at least " + MIN_PASSWORD + " characters.");
        }
    }

    private void apply(AppUser u, UserForm f) {
        u.setFullName(f.getFullName().trim());
        u.setEmail(f.getEmail().trim().toLowerCase());
        u.setRole(f.getRole());
    }
}
