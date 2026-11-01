package com.customerpulse.web;

import com.customerpulse.domain.*;
import com.customerpulse.security.CurrentUser;
import com.customerpulse.service.DashboardService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/** Adds the logged-in user, alert count and enum lists to every page. */
@ControllerAdvice
public class GlobalModelAdvice {

    private final CurrentUser currentUser;
    private final DashboardService dashboard;
    public GlobalModelAdvice(CurrentUser currentUser, DashboardService dashboard) {
        this.currentUser = currentUser;
        this.dashboard = dashboard;
    }

    @ModelAttribute
    public void common(Model model, HttpServletRequest request) {
        model.addAttribute("currentPath", request.getRequestURI());
        model.addAttribute("statuses", CustomerStatus.values());
        model.addAttribute("activityTypes", ActivityType.values());
        model.addAttribute("channels", Channel.values());
        model.addAttribute("roles", Role.values());
        currentUser.find().ifPresent(me -> {
            model.addAttribute("me", me);
            model.addAttribute("alertCount", dashboard.alerts(me).total());
        });
    }
}
