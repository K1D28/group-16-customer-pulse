package com.customerpulse.service;

import com.customerpulse.domain.*;
import com.customerpulse.repository.ActivityRepository;
import com.customerpulse.web.form.ActivityForm;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@Transactional
public class ActivityService {

    private static final Logger log = LoggerFactory.getLogger(ActivityService.class);

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

    @Transactional(readOnly = true)
    public List<Activity> timeline(Long customerId) {
        return activities.findByCustomerIdAndDeletedAtIsNullOrderByActivityDateDesc(customerId);
    }

    @Transactional(readOnly = true)
    public List<Activity> recent(AppUser me, LocalDateTime from, LocalDateTime to, int limit) {
        return activities.findRecent(guard.scope(me), from, to, PageRequest.of(0, limit));
    }

    public Activity log(ActivityForm form, AppUser me) {
        Customer c = customerService.getAccessible(form.getCustomerId(), me);
        Activity a = new Activity();
        a.setCustomer(c);
        a.setUser(me);
        apply(a, form);
        activities.save(a);
        audit.created(me, AuditRecordType.ACTIVITY, a.getId(), c, AuditService.snapshot(a));
        log.info("Logged activity id={} for customer id={}", a.getId(), c.getId());
        return a;
    }

    @Transactional(readOnly = true)
    public Activity getEditable(Long id, AppUser me) {
        Activity a = activities.findById(id).filter(x -> !x.isDeleted())
                .orElseThrow(() -> new NotFoundException("Activity not found"));
        guard.checkAccess(me, a.getCustomer());
        return a;
    }

    public Activity update(Long id, ActivityForm form, AppUser me) {
        Activity a = getEditable(id, me);
        Map<String, Object> before = AuditService.snapshot(a);
        apply(a, form);
        audit.updated(me, AuditRecordType.ACTIVITY, a.getId(), a.getCustomer(), before, AuditService.snapshot(a));
        log.info("Updated activity id={} for customer id={}", a.getId(), a.getCustomer().getId());
        return a;
    }

    public Long delete(Long id, AppUser me) {
        Activity a = getEditable(id, me);
        a.setDeletedAt(LocalDateTime.now());
        audit.deleted(me, AuditRecordType.ACTIVITY, a.getId(), a.getCustomer(), AuditService.snapshot(a));
        log.info("Soft-deleted activity id={} for customer id={}", a.getId(), a.getCustomer().getId());
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