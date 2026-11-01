package com.customerpulse.web;

import com.customerpulse.domain.*;
import com.customerpulse.repository.ActivityRepository;
import com.customerpulse.repository.CustomerRepository;
import com.customerpulse.repository.FollowUpRepository;
import com.customerpulse.repository.StatusHistoryRepository;
import com.customerpulse.service.ActivityService;
import com.customerpulse.service.CustomerService;
import com.customerpulse.service.DashboardService;
import com.customerpulse.service.UserService;
import com.customerpulse.security.CurrentUser;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * UI-10 / FR-08: Manager-only Customer Overview (secured in SecurityConfig).
 * Owner and status filters apply to the customer list; the date range (default: last 30 days)
 * applies to the status transitions and activities shown.
 */
@Controller
@RequestMapping("/overview")
public class OverviewController {

    public static final int DEFAULT_DAYS = 30;

    public record Workload(AppUser user, long customers, long overdue, long stagnant, long activities7d) {}

    private final CurrentUser currentUser;
    private final CustomerService customerService;
    private final CustomerRepository customers;
    private final StatusHistoryRepository history;
    private final FollowUpRepository followUps;
    private final ActivityRepository activityRepo;
    private final ActivityService activities;
    private final DashboardService dashboard;
    private final UserService users;

    public OverviewController(CurrentUser currentUser, CustomerService customerService, CustomerRepository customers,
                              StatusHistoryRepository history, FollowUpRepository followUps,
                              ActivityRepository activityRepo, ActivityService activities,
                              DashboardService dashboard, UserService users) {
        this.currentUser = currentUser;
        this.customerService = customerService;
        this.customers = customers;
        this.history = history;
        this.followUps = followUps;
        this.activityRepo = activityRepo;
        this.activities = activities;
        this.dashboard = dashboard;
        this.users = users;
    }

    @GetMapping
    public String overview(@RequestParam(required = false) Long ownerId,
                           @RequestParam(required = false) CustomerStatus status,
                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                           Model model) {
        AppUser me = currentUser.get();
        boolean defaultRange = from == null && to == null;
        LocalDate fromDate = defaultRange ? LocalDate.now().minusDays(DEFAULT_DAYS) : from;
        LocalDateTime fromTs = fromDate == null ? null : fromDate.atStartOfDay();
        LocalDateTime toTs = to == null ? null : to.plusDays(1).atStartOfDay();

        Map<CustomerStatus, Long> counts = customerService.countByStatus(null);
        long total = counts.values().stream().mapToLong(Long::longValue).sum();
        List<Customer> list = customers.findInScope(ownerId).stream()
                .filter(c -> status == null || c.getCurrentStatus() == status)
                .toList();

        model.addAttribute("counts", counts);
        model.addAttribute("total", total);
        model.addAttribute("customers", list);
        model.addAttribute("lastActivity", customerService.lastActivityByCustomer());
        model.addAttribute("transitions", history.findTransitions(fromTs, toTs, PageRequest.of(0, 25)));
        model.addAttribute("recentActivities", activities.recent(me, fromTs, toTs, 25));
        model.addAttribute("workload", workload());
        model.addAttribute("userOptions", users.all());
        model.addAttribute("ownerId", ownerId);
        model.addAttribute("status", status);
        model.addAttribute("from", fromDate);
        model.addAttribute("to", to);
        model.addAttribute("defaultRange", defaultRange);
        model.addAttribute("defaultDays", DEFAULT_DAYS);
        return "overview";
    }

    /** Customers, overdue follow-ups, stagnant customers and recent activity per person. */
    private List<Workload> workload() {
        Map<Long, Long> overdueByOwner = toMap(followUps.countPerOwner(FollowUpStatus.OVERDUE));
        Map<Long, Long> activityByUser = toMap(activityRepo.countPerUserSince(LocalDateTime.now().minusDays(7)));
        Map<Long, Long> stagnantByOwner = new HashMap<>();
        for (DashboardService.StagnantItem s : dashboard.stagnant(null)) {
            stagnantByOwner.merge(s.customer().getOwner().getId(), 1L, Long::sum);
        }
        List<Workload> rows = new ArrayList<>();
        for (AppUser u : users.all()) {
            rows.add(new Workload(u, customers.countByOwnerIdAndDeletedAtIsNull(u.getId()),
                    overdueByOwner.getOrDefault(u.getId(), 0L),
                    stagnantByOwner.getOrDefault(u.getId(), 0L),
                    activityByUser.getOrDefault(u.getId(), 0L)));
        }
        return rows;
    }

    private static Map<Long, Long> toMap(List<Object[]> rows) {
        Map<Long, Long> map = new HashMap<>();
        for (Object[] r : rows) map.put((Long) r[0], (Long) r[1]);
        return map;
    }
}
