package com.customerpulse.service;

import com.customerpulse.domain.AppUser;
import com.customerpulse.domain.Customer;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

/**
 * NFR-02: server-side access rules for a single-team organization.
 * Staff work only on customers assigned to them; Managers work on all customers.
 * Activities, follow-ups and status history follow the access of their customer.
 */
@Component
public class AccessGuard {

    /** Owner filter for queries: null = all customers (Manager), otherwise the user's own id (Staff). */
    public Long scope(AppUser user) {
        return user.isManager() ? null : user.getId();
    }

    public boolean canAccess(AppUser user, Customer customer) {
        return user.isManager() || customer.getOwner().getId().equals(user.getId());
    }

    /** A soft-deleted customer is "not found" (FR-03); a customer outside the user's scope is refused. */
    public void checkAccess(AppUser user, Customer customer) {
        if (customer.isDeleted()) throw new NotFoundException("Customer not found");
        if (!canAccess(user, customer)) throw new AccessDeniedException("You do not have access to this customer.");
    }

    public void checkManager(AppUser user) {
        if (!user.isManager()) throw new AccessDeniedException("Only Managers can do this.");
    }
}
