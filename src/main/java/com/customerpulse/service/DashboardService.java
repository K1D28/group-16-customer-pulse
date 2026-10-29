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

    public record StagnantItem(Customer customer, LocalDateTime lastActivity, Long daysInactive) {
        public boolean isNeverContacted() { return lastActivity == null; }
    }

    public record Alerts(List<FollowUp> overdue, List<StagnantItem> stagnant) {
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

    public Alerts alerts(AppUser me) {
        // Oldest due date first = most urgent
        List<FollowUp> overdue = followUps.findInScope(guard.scope(me), guard.team(me), EnumSet.of(FollowUpStatus.OVERDUE));
        return new Alerts(overdue, stagnant(guard.scope(me), guard.team(me)));
    }

    /** Never-contacted customers first, then the longest silence. */
    public List<StagnantItem> stagnant(Long ownerId, Long teamId) {
        Map<Long, LocalDateTime> last = customerService.lastActivityByCustomer();
        LocalDateTime now = LocalDateTime.now();
        List<StagnantItem> result = new ArrayList<>();
        for (Customer c : customers.findInScope(ownerId, teamId)) {
            LocalDateTime when = last.get(c.getId());
            if (when == null) {
                result.add(new StagnantItem(c, null, null));
            } else {
                long days = ChronoUnit.DAYS.between(when.toLocalDate(), now.toLocalDate());
                if (days > STAGNANT_DAYS) result.add(new StagnantItem(c, when, days));
            }
        }
        result.sort(Comparator.comparing((StagnantItem s) -> s.daysInactive() == null ? Long.MAX_VALUE : s.daysInactive())
                .reversed());
        return result;
    }

    public List<FollowUp> upcoming(AppUser me, int days) {
        LocalDate limit = LocalDate.now().plusDays(days);
        return followUps.findInScope(guard.scope(me), guard.team(me), EnumSet.of(FollowUpStatus.PENDING)).stream()
                .filter(f -> !f.getDueDate().isAfter(limit))
                .toList();
    }

    public long activitiesThisWeek(AppUser me) {
        return activities.countSince(guard.scope(me), guard.team(me), LocalDateTime.now().minusDays(7));
    }
}
