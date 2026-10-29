package com.customerpulse.web;

import com.customerpulse.domain.AppUser;
import com.customerpulse.security.CurrentUser;
import com.customerpulse.service.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class DashboardController {

    private final CurrentUser currentUser;
    private final DashboardService dashboard;
    private final CustomerService customers;
    private final ActivityService activities;
    private final AccessGuard guard;
    private final CaseService cases;

    public DashboardController(CurrentUser currentUser, DashboardService dashboard, CustomerService customers,
                               ActivityService activities, AccessGuard guard, CaseService cases) {
        this.cases = cases;
        this.currentUser = currentUser;
        this.dashboard = dashboard;
        this.customers = customers;
        this.activities = activities;
        this.guard = guard;
    }

    /** UI-02 / FR-07. */
    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        AppUser me = currentUser.get();
        DashboardService.Alerts alerts = dashboard.alerts(me);
        var statusCounts = customers.countByStatus(guard.scope(me), guard.team(me));
        model.addAttribute("alerts", alerts);
        model.addAttribute("statusCounts", statusCounts);
        model.addAttribute("customerTotal", statusCounts.values().stream().mapToLong(Long::longValue).sum());
        model.addAttribute("upcoming", dashboard.upcoming(me, 7));
        model.addAttribute("recent", activities.recent(me, 6));
        model.addAttribute("weekActivities", dashboard.activitiesThisWeek(me));
        model.addAttribute("stagnantDays", DashboardService.STAGNANT_DAYS);
        model.addAttribute("myCases", cases.myOpenWork(me));
        return "dashboard";
    }
}
