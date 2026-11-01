package com.customerpulse.service;

import com.customerpulse.domain.*;
import com.customerpulse.repository.ActivityRepository;
import com.customerpulse.repository.CustomerRepository;
import com.customerpulse.repository.FollowUpRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** FR-07: overdue follow-ups and stagnant customers, plus summary numbers. */
@Service
@Transactional(readOnly = true)
public class DashboardService {

    /** A customer is stagnant after more than this many days without activity. */
    public static final int STAGNANT_DAYS = 14;
    /** Only these statuses can be stagnant; HOLD, INACTIVE and LOST are never flagged. */
    public static final Set<CustomerStatus> WATCHED = EnumSet.of(CustomerStatus.NEGOTIATING, CustomerStatus.ACTIVE);

    /**
     * @param lastActivity latest non-deleted activity, or null when there is none yet
     * @param referenceDate the latest activity date, or the customer's creation date if there is no activity
     */
    public record StagnantItem(Customer customer, LocalDateTime lastActivity, LocalDateTime referenceDate, long daysInactive) {
        public boolean isNeverContacted() { return lastActivity == null; }
    }

    public record Alerts(List<FollowUp> overdue, List<StagnantItem> stagnant, LocalDateTime refreshedAt) {
        public int total() { return overdue.size() + stagnant.size(); }
    }

    private final CustomerRepository customers;
    private final FollowUpRepository followUps;
    private final ActivityRepository activities;
    private final CustomerService customerService;
    private final AccessGuard guard;

    public DashboardService(CustomerRepository customers, FollowUpRepository followUps, ActivityRepository activities,
                            CustomerService customerService, AccessGuard guard) {
        this.customers = customers;
        this.followUps = followUps;
        this.activities = activities;
        this.customerService = customerService;
        this.guard = guard;
    }

    /** Data as of now; the page does not refresh itself. Oldest due date first; most days inactive first. */
    public Alerts alerts(AppUser me) {
        List<FollowUp> overdue = followUps.findInScope(guard.scope(me), EnumSet.of(FollowUpStatus.OVERDUE));
        return new Alerts(overdue, stagnant(guard.scope(me)), LocalDateTime.now());
    }

    /** Stagnant: NEGOTIATING or ACTIVE, and more than 14 days since the latest activity (or creation date). */
    public List<StagnantItem> stagnant(Long ownerId) {
        Map<Long, LocalDateTime> last = customerService.lastActivityByCustomer();
        LocalDate today = LocalDate.now();
        List<StagnantItem> result = new ArrayList<>();
        for (Customer c : customers.findInScopeWithStatus(ownerId, WATCHED)) {
            LocalDateTime lastActivity = last.get(c.getId());
            LocalDateTime reference = lastActivity != null ? lastActivity : c.getCreatedAt();
            long days = ChronoUnit.DAYS.between(reference.toLocalDate(), today);
            if (days > STAGNANT_DAYS) result.add(new StagnantItem(c, lastActivity, reference, days));
        }
        result.sort(Comparator.comparingLong(StagnantItem::daysInactive).reversed()
                .thenComparing(s -> s.customer().getCompanyName()));
        return result;
    }

    public List<FollowUp> upcoming(AppUser me, int days) {
        LocalDate limit = LocalDate.now().plusDays(days);
        return followUps.findInScope(guard.scope(me), EnumSet.of(FollowUpStatus.PENDING)).stream()
                .filter(f -> !f.getDueDate().isAfter(limit))
                .toList();
    }

    public long activitiesThisWeek(AppUser me) {
        return activities.countSince(guard.scope(me), LocalDateTime.now().minusDays(7));
    }
}
