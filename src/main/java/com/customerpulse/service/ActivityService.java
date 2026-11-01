package com.customerpulse.service;

import com.customerpulse.domain.*;
import com.customerpulse.repository.ActivityRepository;
import com.customerpulse.web.form.ActivityForm;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** FR-04: log, edit and soft-delete activities for customers in the user's scope. */
@Service
@Transactional
public class ActivityService {

    private final ActivityRepository activities;
    private final CustomerService customerService;
    private final AccessGuard guard;
    private final AuditService audit;

    public ActivityService(ActivityRepository activities, CustomerService customerService, AccessGuard guard,
                           AuditService audit) {
        this.activities = activities;
        this.customerService = customerService;
        this.guard = guard;
        this.audit = audit;
    }

    /** FR-03: the customer's timeline, newest first, without deleted activities. */
    @Transactional(readOnly = true)
    public List<Activity> timeline(Long customerId) {
        return activities.findByCustomerIdAndDeletedAtIsNullOrderByActivityDateDesc(customerId);
    }

    @Transactional(readOnly = true)
    public List<Activity> recent(AppUser me, LocalDateTime from, LocalDateTime to, int limit) {
        return activities.findRecent(guard.scope(me), from, to, PageRequest.of(0, limit));
    }

    /** The logged-in user is saved as the logger. */
    public Activity log(ActivityForm form, AppUser me) {
        Customer c = customerService.getAccessible(form.getCustomerId(), me);
        Activity a = new Activity();
        a.setCustomer(c);
        a.setUser(me);
        apply(a, form);
        activities.save(a);
        audit.created(me, AuditRecordType.ACTIVITY, a.getId(), c, AuditService.snapshot(a));
        return a;
    }

    /** The customer's owner or a Manager can change any of its activities, including a previous owner's. */
    @Transactional(readOnly = true)
    public Activity getEditable(Long id, AppUser me) {
        Activity a = activities.findById(id).filter(x -> !x.isDeleted())
                .orElseThrow(() -> new NotFoundException("Activity not found"));
        guard.checkAccess(me, a.getCustomer());
        return a;
    }

    /** The original logger is kept; the editor is recorded in the audit log. */
    public Activity update(Long id, ActivityForm form, AppUser me) {
        Activity a = getEditable(id, me);
        Map<String, Object> before = AuditService.snapshot(a);
        apply(a, form);
        audit.updated(me, AuditRecordType.ACTIVITY, a.getId(), a.getCustomer(), before, AuditService.snapshot(a));
        return a;
    }

    /** Soft delete. Follow-ups created from this activity are not changed and keep their link. */
    public Long delete(Long id, AppUser me) {
        Activity a = getEditable(id, me);
        a.setDeletedAt(LocalDateTime.now());
        audit.deleted(me, AuditRecordType.ACTIVITY, a.getId(), a.getCustomer(), AuditService.snapshot(a));
        return a.getCustomer().getId();
    }

    private void apply(Activity a, ActivityForm f) {
        if (f.getActivityDate() != null && f.getActivityDate().isAfter(LocalDateTime.now())) {
            throw new BusinessException("The activity date cannot be in the future.");
        }
        a.setActivityType(f.getActivityType());
        a.setChannel(f.getChannel());
        a.setOutcome(f.getOutcome().trim());
        a.setActivityDate(f.getActivityDate());
    }
}
