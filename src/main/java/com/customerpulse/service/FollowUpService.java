package com.customerpulse.service;

import com.customerpulse.domain.*;
import com.customerpulse.repository.ActivityRepository;
import com.customerpulse.repository.FollowUpRepository;
import com.customerpulse.repository.UserRepository;
import com.customerpulse.web.form.FollowUpForm;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

@Service
@Transactional
public class FollowUpService {

    private static final Logger log = LoggerFactory.getLogger(FollowUpService.class);

    public static final Set<FollowUpStatus> OPEN = EnumSet.of(FollowUpStatus.PENDING, FollowUpStatus.OVERDUE);

    private final FollowUpRepository followUps;
    private final ActivityRepository activities;
    private final UserRepository users;
    private final CustomerService customerService;
    private final AccessGuard guard;

    public FollowUpService(FollowUpRepository followUps, ActivityRepository activities, UserRepository users,
                           CustomerService customerService, AccessGuard guard) {
        this.followUps = followUps;
        this.activities = activities;
        this.users = users;
        this.customerService = customerService;
        this.guard = guard;
    }

    /** NFR-09: refresh overdue flags every night at 00:05 and when the app starts. */
    @Scheduled(cron = "0 5 0 * * *")
    @EventListener(ApplicationReadyEvent.class)
    public void refreshOverdue() {
        int changed = followUps.markOverdue(LocalDate.now());
        if (changed > 0) log.info("Marked {} follow-up(s) as OVERDUE", changed);
    }

    @Transactional(readOnly = true)
    public List<FollowUp> forCustomer(Long customerId) {
        return followUps.findByCustomerIdOrderByDueDateAsc(customerId);
    }

    @Transactional(readOnly = true)
    public List<FollowUp> inScope(AppUser me, Set<FollowUpStatus> statuses) {
        return followUps.findInScope(guard.scope(me), guard.team(me), statuses);
    }

    public FollowUp create(FollowUpForm form, AppUser me) {
        Customer c = customerService.getAccessible(form.getCustomerId(), me);
        FollowUp f = new FollowUp();
        f.setCustomer(c);
        apply(f, form, me);
        f.setStatus(statusForDueDate(form.getDueDate()));
        return followUps.save(f);
    }

    @Transactional(readOnly = true)
    public FollowUp getEditable(Long id, AppUser me) {
        FollowUp f = followUps.findById(id).orElseThrow(() -> new NotFoundException("Follow-up not found"));
        if (!guard.canAccess(me, f)) throw new AccessDeniedException("You do not have access to this follow-up.");
        return f;
    }

    public FollowUp update(Long id, FollowUpForm form, AppUser me) {
        FollowUp f = getEditable(id, me);
        apply(f, form, me);
        if (f.isOpen()) f.setStatus(statusForDueDate(form.getDueDate()));
        return f;
    }

    public FollowUp complete(Long id, AppUser me) {
        FollowUp f = getEditable(id, me);
        f.setStatus(FollowUpStatus.COMPLETED);
        return f;
    }

    public FollowUp reopen(Long id, AppUser me) {
        FollowUp f = getEditable(id, me);
        f.setStatus(statusForDueDate(f.getDueDate()));
        return f;
    }

    public Long delete(Long id, AppUser me) {
        FollowUp f = getEditable(id, me);
        Long customerId = f.getCustomer().getId();
        followUps.delete(f);
        return customerId;
    }

    private void apply(FollowUp f, FollowUpForm form, AppUser me) {
        AppUser assignee = users.findById(form.getAssignedUserId())
                .orElseThrow(() -> new BusinessException("Selected user does not exist."));
        if (!assignee.sameTeam(f.getCustomer().getOwner())) {
            throw new BusinessException("Follow-ups can only be assigned to someone in the customer's team.");
        }
        boolean changing = f.getAssignedUser() == null || !f.getAssignedUser().getId().equals(assignee.getId());
        if (changing && !assignee.isActive()) {
            throw new BusinessException(assignee.getFullName() + " is not an active user.");
        }
        // Staff can only hand follow-ups to themselves or the customer owner
        if (!me.isManager() && !assignee.getId().equals(me.getId())
                && !assignee.getId().equals(f.getCustomer().getOwner().getId())) {
            throw new BusinessException("Staff can assign follow-ups only to themselves or the account owner.");
        }
        f.setAssignedUser(assignee);
        f.setDueDate(form.getDueDate());
        f.setNotes(CustomerService.blankToNull(form.getNotes()));
        if (form.getActivityId() != null) {
            Activity a = activities.findById(form.getActivityId())
                    .filter(act -> act.getCustomer().getId().equals(f.getCustomer().getId()))
                    .orElseThrow(() -> new BusinessException("The linked activity belongs to another customer."));
            f.setActivity(a);
        } else {
            f.setActivity(null);
        }
    }

    private static FollowUpStatus statusForDueDate(LocalDate due) {
        return due.isBefore(LocalDate.now()) ? FollowUpStatus.OVERDUE : FollowUpStatus.PENDING;
    }
}
