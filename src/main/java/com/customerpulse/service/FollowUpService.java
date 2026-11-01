package com.customerpulse.service;

import com.customerpulse.domain.*;
import com.customerpulse.repository.ActivityRepository;
import com.customerpulse.repository.FollowUpRepository;
import com.customerpulse.web.form.FollowUpForm;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** FR-05: follow-ups for customers in the user's scope. */
@Service
@Transactional
public class FollowUpService {

    private static final Logger log = LoggerFactory.getLogger(FollowUpService.class);

    public static final Set<FollowUpStatus> OPEN = EnumSet.of(FollowUpStatus.PENDING, FollowUpStatus.OVERDUE);

    private final FollowUpRepository followUps;
    private final ActivityRepository activities;
    private final CustomerService customerService;
    private final AccessGuard guard;
    private final AuditService audit;

    public FollowUpService(FollowUpRepository followUps, ActivityRepository activities,
                           CustomerService customerService, AccessGuard guard, AuditService audit) {
        this.followUps = followUps;
        this.activities = activities;
        this.customerService = customerService;
        this.guard = guard;
        this.audit = audit;
    }

    /**
     * Daily job (00:05 Thailand time) and on start-up: PENDING follow-ups whose due date is before
     * today become OVERDUE. A follow-up due today stays PENDING. This is a system action and is not audited.
     */
    @Scheduled(cron = "0 5 0 * * *", zone = "Asia/Bangkok")
    @EventListener(ApplicationReadyEvent.class)
    public void refreshOverdue() {
        int changed = followUps.markOverdue(LocalDate.now());
        if (changed > 0) log.info("Marked {} follow-up(s) as OVERDUE", changed);
    }

    @Transactional(readOnly = true)
    public List<FollowUp> forCustomer(Long customerId) {
        return followUps.findByCustomerIdAndDeletedAtIsNullOrderByDueDateAsc(customerId);
    }

    @Transactional(readOnly = true)
    public List<FollowUp> inScope(AppUser me, Set<FollowUpStatus> statuses) {
        return followUps.findInScope(guard.scope(me), statuses);
    }

    /** Saved as PENDING and assigned to the customer's current owner automatically. */
    public FollowUp create(FollowUpForm form, AppUser me) {
        Customer c = customerService.getAccessible(form.getCustomerId(), me);
        requireTodayOrLater(form.getDueDate());
        FollowUp f = new FollowUp();
        f.setCustomer(c);
        f.setAssignedUser(c.getOwner());
        f.setDueDate(form.getDueDate());
        f.setStatus(FollowUpStatus.PENDING);
        f.setNotes(CustomerService.blankToNull(form.getNotes()));
        f.setActivity(linkedActivity(form.getActivityId(), c));
        followUps.save(f);
        audit.created(me, AuditRecordType.FOLLOW_UP, f.getId(), c, AuditService.snapshot(f));
        return f;
    }

    @Transactional(readOnly = true)
    public FollowUp getAccessible(Long id, AppUser me) {
        FollowUp f = followUps.findById(id).filter(x -> !x.isDeleted())
                .orElseThrow(() -> new NotFoundException("Follow-up not found"));
        guard.checkAccess(me, f.getCustomer());
        return f;
    }

    /**
     * COMPLETED follow-ups are read-only. The due date must be today or later only when it changes;
     * editing just the notes keeps a past date. An OVERDUE follow-up with a new date returns to PENDING.
     */
    public FollowUp update(Long id, FollowUpForm form, AppUser me) {
        FollowUp f = getAccessible(id, me);
        if (!f.isOpen()) throw new BusinessException("Completed follow-ups are read-only.");
        Map<String, Object> before = AuditService.snapshot(f);
        if (!Objects.equals(form.getDueDate(), f.getDueDate())) {
            requireTodayOrLater(form.getDueDate());
            f.setDueDate(form.getDueDate());
            if (f.getStatus() == FollowUpStatus.OVERDUE) f.setStatus(FollowUpStatus.PENDING);
        }
        f.setNotes(CustomerService.blankToNull(form.getNotes()));
        f.setActivity(linkedActivity(form.getActivityId(), f.getCustomer()));
        audit.updated(me, AuditRecordType.FOLLOW_UP, f.getId(), f.getCustomer(), before, AuditService.snapshot(f));
        return f;
    }

    /** Sets COMPLETED and records the completion time. Completed follow-ups cannot be reopened. */
    public FollowUp complete(Long id, AppUser me) {
        FollowUp f = getAccessible(id, me);
        if (!f.isOpen()) throw new BusinessException("This follow-up is already completed.");
        Map<String, Object> before = AuditService.snapshot(f);
        f.setStatus(FollowUpStatus.COMPLETED);
        f.setCompletedAt(LocalDateTime.now());
        audit.updated(me, AuditRecordType.FOLLOW_UP, f.getId(), f.getCustomer(), before, AuditService.snapshot(f));
        return f;
    }

    /** Soft delete; allowed for open and completed follow-ups. */
    public Long delete(Long id, AppUser me) {
        FollowUp f = getAccessible(id, me);
        f.setDeletedAt(LocalDateTime.now());
        audit.deleted(me, AuditRecordType.FOLLOW_UP, f.getId(), f.getCustomer(), AuditService.snapshot(f));
        return f.getCustomer().getId();
    }

    private static void requireTodayOrLater(LocalDate due) {
        if (due == null) throw new BusinessException("Due date is required.");
        if (due.isBefore(LocalDate.now())) throw new BusinessException("The due date must be today or later.");
    }

    /** The linked activity is optional and must belong to the same customer. */
    private Activity linkedActivity(Long activityId, Customer c) {
        if (activityId == null) return null;
        return activities.findById(activityId)
                .filter(a -> a.getCustomer().getId().equals(c.getId()))
                .orElseThrow(() -> new BusinessException("The linked activity belongs to another customer."));
    }
}
