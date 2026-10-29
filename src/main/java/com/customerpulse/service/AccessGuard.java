package com.customerpulse.service;

import com.customerpulse.domain.AppUser;
import com.customerpulse.domain.Customer;
import com.customerpulse.domain.FollowUp;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

/**
 * NFR-02: server-side access rules.
 * Staff only touch customers they own; Managers can access every team customer.
 */
@Component
public class AccessGuard {

    /** Owner filter for queries: null = everything (Manager), otherwise the user's own id. */
    public Long scope(AppUser user) {
        return user.isManager() ? null : user.getId();
    }

    /** Team filter for queries: a Manager's own team; null for Staff (the owner filter already covers them). */
    public Long team(AppUser user) {
        return user.isManager() ? user.getTeam().getId() : null;
    }

    /** Staff: customers they own. Managers: customers owned by anyone in their team. */
    public boolean canAccess(AppUser user, Customer customer) {
        if (user.isManager()) return user.sameTeam(customer.getOwner());
        return customer.getOwner().getId().equals(user.getId());
    }

    /** Managers can only manage people in their own team. */
    public void checkSameTeam(AppUser user, AppUser other) {
        if (!user.sameTeam(other)) throw new AccessDeniedException("That person is in another team.");
    }

    public void checkAccess(AppUser user, Customer customer) {
        if (!canAccess(user, customer)) {
            throw new AccessDeniedException("You do not have access to this customer.");
        }
    }

    public boolean canAccess(AppUser user, FollowUp followUp) {
        return canAccess(user, followUp.getCustomer())
                || followUp.getAssignedUser().getId().equals(user.getId());
    }

    public void checkManager(AppUser user) {
        if (!user.isManager()) {
            throw new AccessDeniedException("Only Managers can do this.");
        }
    }
}
