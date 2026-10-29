package com.customerpulse.web;

import com.customerpulse.domain.*;
import com.customerpulse.security.CurrentUser;
import com.customerpulse.service.*;
import com.customerpulse.web.form.FollowUpForm;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

@Controller
@RequestMapping("/followups")
public class FollowUpController {

    private final CurrentUser currentUser;
    private final FollowUpService followUps;
    private final CustomerService customers;
    private final ActivityService activities;
    private final UserService users;

    public FollowUpController(CurrentUser currentUser, FollowUpService followUps, CustomerService customers,
                              ActivityService activities, UserService users) {
        this.currentUser = currentUser;
        this.followUps = followUps;
        this.customers = customers;
        this.activities = activities;
        this.users = users;
    }

    /** Follow-up tasks list with tabs: open / overdue / completed / all. */
    @GetMapping
    public String list(@RequestParam(defaultValue = "open") String tab, Model model) {
        Set<FollowUpStatus> statuses = switch (tab) {
            case "overdue" -> EnumSet.of(FollowUpStatus.OVERDUE);
            case "completed" -> EnumSet.of(FollowUpStatus.COMPLETED);
            case "all" -> EnumSet.allOf(FollowUpStatus.class);
            default -> FollowUpService.OPEN;
        };
        AppUser me = currentUser.get();
        List<FollowUp> items = followUps.inScope(me, statuses);
        if ("completed".equals(tab)) items = items.reversed();
        model.addAttribute("items", items);
        model.addAttribute("tab", tab);
        model.addAttribute("openCount", followUps.inScope(me, FollowUpService.OPEN).size());
        model.addAttribute("overdueCount", followUps.inScope(me, EnumSet.of(FollowUpStatus.OVERDUE)).size());
        return "followups/list";
    }

    @GetMapping("/new")
    public String newForm(@RequestParam(required = false) Long customerId,
                          @RequestParam(required = false) Long activityId, Model model) {
        AppUser me = currentUser.get();
        FollowUpForm form = new FollowUpForm();
        form.setAssignedUserId(me.getId());
        if (customerId != null) {
            customers.getAccessible(customerId, me);
            form.setCustomerId(customerId);
            form.setActivityId(activityId);
        }
        model.addAttribute("form", form);
        return formView(model, null, customerId);
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("form") FollowUpForm form, BindingResult errors,
                         Model model, RedirectAttributes flash) {
        if (errors.hasErrors()) return formView(model, null, form.getCustomerId());
        try {
            FollowUp f = followUps.create(form, currentUser.get());
            flash.addFlashAttribute("success", "Follow-up scheduled for " + f.getAssignedUser().getFullName() + ".");
            return "redirect:/customers/" + f.getCustomer().getId();
        } catch (BusinessException e) {
            model.addAttribute("error", e.getMessage());
            return formView(model, null, form.getCustomerId());
        }
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        FollowUp f = followUps.getEditable(id, currentUser.get());
        model.addAttribute("form", FollowUpForm.from(f));
        return formView(model, f, f.getCustomer().getId());
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id, @Valid @ModelAttribute("form") FollowUpForm form,
                         BindingResult errors, Model model, RedirectAttributes flash) {
        AppUser me = currentUser.get();
        FollowUp existing = followUps.getEditable(id, me);
        form.setCustomerId(existing.getCustomer().getId());
        if (errors.hasErrors()) return formView(model, existing, form.getCustomerId());
        try {
            followUps.update(id, form, me);
        } catch (BusinessException e) {
            model.addAttribute("error", e.getMessage());
            return formView(model, existing, form.getCustomerId());
        }
        flash.addFlashAttribute("success", "Follow-up updated.");
        return "redirect:/customers/" + existing.getCustomer().getId();
    }

    @PostMapping("/{id}/complete")
    public String complete(@PathVariable Long id, @RequestParam(required = false) String back, RedirectAttributes flash) {
        FollowUp f = followUps.complete(id, currentUser.get());
        flash.addFlashAttribute("success", "Follow-up marked as completed.");
        return redirectBack(back, f.getCustomer().getId());
    }

    @PostMapping("/{id}/reopen")
    public String reopen(@PathVariable Long id, @RequestParam(required = false) String back, RedirectAttributes flash) {
        FollowUp f = followUps.reopen(id, currentUser.get());
        flash.addFlashAttribute("success", "Follow-up reopened.");
        return redirectBack(back, f.getCustomer().getId());
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, @RequestParam(required = false) String back, RedirectAttributes flash) {
        Long customerId = followUps.delete(id, currentUser.get());
        flash.addFlashAttribute("success", "Follow-up deleted.");
        return redirectBack(back, customerId);
    }

    /** Only local paths are accepted, so this cannot redirect off-site. */
    private static String redirectBack(String back, Long customerId) {
        if (back != null && back.startsWith("/") && !back.startsWith("//")) return "redirect:" + back;
        return "redirect:/customers/" + customerId;
    }

    private String formView(Model model, FollowUp existing, Long customerId) {
        AppUser me = currentUser.get();
        model.addAttribute("followUp", existing);
        model.addAttribute("customerOptions", customers.listInScope(me));
        model.addAttribute("userOptions", users.activeTeam(me));
        if (customerId != null) {
            Customer c = customers.getAccessible(customerId, me);
            model.addAttribute("customer", c);
            model.addAttribute("activityOptions", activities.timeline(c.getId()));
        }
        return "followups/form";
    }
}
