package com.customerpulse.web;

import com.customerpulse.domain.*;
import com.customerpulse.repository.ActivityRepository;
import com.customerpulse.repository.CustomerRepository;
import com.customerpulse.repository.FollowUpRepository;
import com.customerpulse.repository.StatusHistoryRepository;
import com.customerpulse.security.CurrentUser;
import com.customerpulse.service.BusinessException;
import com.customerpulse.service.CustomerService;
import com.customerpulse.service.TeamService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import com.customerpulse.service.DashboardService;
import com.customerpulse.service.UserService;
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

/** FR-08: Manager-only team overview (secured in SecurityConfig). */
@Controller
@RequestMapping("/team")
public class TeamController {

    public record Workload(AppUser user, long customers, long overdue, long stagnant, long activities7d) {}

    private final CustomerService customerService;
    private final CustomerRepository customers;
    private final StatusHistoryRepository history;
    private final FollowUpRepository followUps;
    private final ActivityRepository activities;
    private final DashboardService dashboard;
    private final UserService users;
    private final CurrentUser currentUser;
    private final TeamService teamService;

    public TeamController(CurrentUser currentUser, TeamService teamService, CustomerService customerService, CustomerRepository customers, StatusHistoryRepository history,
                          FollowUpRepository followUps, ActivityRepository activities, DashboardService dashboard,
                          UserService users) {
        this.currentUser = currentUser;
        this.teamService = teamService;
        this.customerService = customerService;
        this.customers = customers;
        this.history = history;
        this.followUps = followUps;
        this.activities = activities;
        this.dashboard = dashboard;
        this.users = users;
    }

    @GetMapping
    public String overview(@RequestParam(required = false) String q,
                           @RequestParam(required = false) Long ownerId,
                           @RequestParam(required = false) CustomerStatus status,
                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                           Model model) {
        AppUser me = currentUser.get();
        Long teamId = me.getTeam().getId();
        Map<CustomerStatus, Long> counts = customerService.countByStatus(ownerId, teamId);
        long total = counts.values().stream().mapToLong(Long::longValue).sum();

        List<Customer> list = customers.findInScope(ownerId, teamId).stream()
                .filter(c -> status == null || c.getCurrentStatus() == status)
                .filter(c -> q == null || q.isBlank() || matches(c, q.trim().toLowerCase()))
                .toList();

        LocalDateTime fromTs = from == null ? null : from.atStartOfDay();
        LocalDateTime toTs = to == null ? null : to.plusDays(1).atStartOfDay();
        List<StatusHistory> transitions = history.findTransitions(ownerId, teamId, fromTs, toTs, PageRequest.of(0, 25));

        // Workload per person
        Map<Long, Long> overdueByOwner = toMap(followUps.countPerOwner(FollowUpStatus.OVERDUE));
        Map<Long, Long> activityByUser = toMap(activities.countPerUserSince(LocalDateTime.now().minusDays(7)));
        Map<Long, Long> stagnantByOwner = new HashMap<>();
        for (DashboardService.StagnantItem s : dashboard.stagnant(null, teamId)) {
            stagnantByOwner.merge(s.customer().getOwner().getId(), 1L, Long::sum);
        }
        List<Workload> workload = new ArrayList<>();
        List<AppUser> members = users.team(me);
        for (AppUser u : members) {
            workload.add(new Workload(u, customers.countByOwnerId(u.getId()),
                    overdueByOwner.getOrDefault(u.getId(), 0L),
                    stagnantByOwner.getOrDefault(u.getId(), 0L),
                    activityByUser.getOrDefault(u.getId(), 0L)));
        }

        model.addAttribute("counts", counts);
        model.addAttribute("total", total);
        model.addAttribute("customers", list);
        model.addAttribute("lastActivity", customerService.lastActivityByCustomer());
        model.addAttribute("transitions", transitions);
        model.addAttribute("workload", workload);
        model.addAttribute("userOptions", members);
        model.addAttribute("team", me.getTeam());
        model.addAttribute("ownerId", ownerId);
        model.addAttribute("q", q);
        model.addAttribute("status", status);
        model.addAttribute("from", from);
        model.addAttribute("to", to);
        return "team/overview";
    }

    /** A Manager renames their team. */
    @PostMapping("/name")
    public String rename(@RequestParam String name, RedirectAttributes flash) {
        try {
            teamService.rename(name, currentUser.get());
            flash.addFlashAttribute("success", "Team renamed to \"" + name.trim() + "\".");
        } catch (BusinessException e) {
            flash.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/team";
    }

    private static boolean matches(Customer c, String q) {
        return c.getCompanyName().toLowerCase().contains(q)
                || (c.getContactName() != null && c.getContactName().toLowerCase().contains(q));
    }

    private static Map<Long, Long> toMap(List<Object[]> rows) {
        Map<Long, Long> map = new HashMap<>();
        for (Object[] r : rows) map.put((Long) r[0], (Long) r[1]);
        return map;
    }
}
