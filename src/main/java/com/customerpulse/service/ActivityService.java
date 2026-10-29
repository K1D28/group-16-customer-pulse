package com.customerpulse.service;

import com.customerpulse.domain.*;
import com.customerpulse.repository.ActivityRepository;
import com.customerpulse.repository.FollowUpRepository;
import com.customerpulse.web.form.ActivityForm;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class ActivityService {

    public static final int PAGE_SIZE = 15;

    private final ActivityRepository activities;
    private final FollowUpRepository followUps;
    private final CustomerService customerService;
    private final CaseService caseService;
    private final AccessGuard guard;

    public ActivityService(ActivityRepository activities, FollowUpRepository followUps,
                           CustomerService customerService, CaseService caseService, AccessGuard guard) {
        this.activities = activities;
        this.followUps = followUps;
        this.customerService = customerService;
        this.caseService = caseService;
        this.guard = guard;
    }

    @Transactional(readOnly = true)
    public List<Activity> forCase(Long caseId) {
        return activities.findByServiceCaseIdOrderByActivityDateDesc(caseId);
    }

    @Transactional(readOnly = true)
    public List<Activity> timeline(Long customerId) {
        return activities.findByCustomerIdOrderByActivityDateDesc(customerId);
    }

    @Transactional(readOnly = true)
    public Page<Activity> search(AppUser me, ActivityType type, Channel channel, String q, int page) {
        String text = (q == null || q.isBlank()) ? null : q.trim();
        return activities.search(guard.scope(me), guard.team(me), type, channel, text, PageRequest.of(Math.max(page, 0), PAGE_SIZE));
    }

    @Transactional(readOnly = true)
    public List<Activity> recent(AppUser me, int limit) {
        return activities.search(guard.scope(me), guard.team(me), null, null, null, PageRequest.of(0, limit)).getContent();
    }

    /**
     * FR-04: the logged-in user is saved as the logger.
     * With a case, any assignee may log it, even when someone else owns the customer.
     */
    public Activity log(ActivityForm form, AppUser me) {
        Activity a = new Activity();
        if (form.getCaseId() != null) {
            ServiceCase sc = caseService.getViewable(form.getCaseId(), me);
            if (!caseService.canLog(sc, me)) {
                throw new AccessDeniedException(sc.isClosed()
                        ? "This case is closed." : "Only people assigned to this case can log activity on it.");
            }
            a.setServiceCase(sc);
            a.setCustomer(sc.getCustomer());
        } else {
            a.setCustomer(customerService.getAccessible(form.getCustomerId(), me));
        }
        a.setUser(me);
        apply(a, form);
        activities.save(a);
        if (a.getServiceCase() != null) caseService.onActivityLogged(a.getServiceCase());
        return a;
    }

    @Transactional(readOnly = true)
    public Activity getEditable(Long id, AppUser me) {
        Activity a = activities.findById(id).orElseThrow(() -> new NotFoundException("Activity not found"));
        if (!me.isManager() && !a.getUser().getId().equals(me.getId())) {
            throw new AccessDeniedException("You can only change activities you logged.");
        }
        return a;
    }

    public Activity update(Long id, ActivityForm form, AppUser me) {
        Activity a = getEditable(id, me);
        apply(a, form);
        return a;
    }

    /** Follow-ups created from this activity are kept; they just lose the link. */
    public Long delete(Long id, AppUser me) {
        Activity a = getEditable(id, me);
        Long customerId = a.getCustomer().getId();
        followUps.detachActivity(id);
        activities.deleteById(id);
        return customerId;
    }

    private void apply(Activity a, ActivityForm f) {
        a.setActivityType(f.getActivityType());
        a.setChannel(f.getChannel());
        a.setOutcome(f.getOutcome().trim());
        a.setActivityDate(f.getActivityDate());
    }
}
