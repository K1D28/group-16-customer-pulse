package com.customerpulse.web;

import com.customerpulse.domain.AppUser;
import com.customerpulse.domain.Customer;
import com.customerpulse.domain.FollowUp;
import com.customerpulse.security.CurrentUser;
import com.customerpulse.service.*;
import com.customerpulse.web.form.FollowUpForm;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.Objects;

/** UI-07 / FR-05: add, edit, complete and delete follow-ups. */
@Controller
@RequestMapping("/followups")
public class FollowUpController {

    private final CurrentUser currentUser;
    private final FollowUpService followUps;
    private final CustomerService customers;
    private final ActivityService activities;

    public FollowUpController(CurrentUser currentUser, FollowUpService followUps, CustomerService customers,
                              ActivityService activities) {
        this.currentUser = currentUser;
        this.followUps = followUps;
        this.customers = customers;
        this.activities = activities;
    }

    @GetMapping("/new")
    public String newForm(@RequestParam(required = false) Long customerId,
                          @RequestParam(required = false) Long activityId, Model model) {
        FollowUpForm form = new FollowUpForm();
        if (customerId != null) {
            customers.getAccessible(customerId, currentUser.get());
            form.setCustomerId(customerId);
            form.setActivityId(activityId);
        }
        model.addAttribute("form", form);
        return formView(model, null, customerId);
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("form") FollowUpForm form, BindingResult errors,
                         Model model, RedirectAttributes flash) {
        rejectPastDate(form.getDueDate(), errors);
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
    public String editForm(@PathVariable Long id, Model model, RedirectAttributes flash) {
        FollowUp f = followUps.getAccessible(id, currentUser.get());
        if (!f.isOpen()) {
            flash.addFlashAttribute("info", "Completed follow-ups are read-only.");
            return "redirect:/customers/" + f.getCustomer().getId();
        }
        model.addAttribute("form", FollowUpForm.from(f));
        return formView(model, f, f.getCustomer().getId());
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id, @Valid @ModelAttribute("form") FollowUpForm form,
                         BindingResult errors, Model model, RedirectAttributes flash) {
        AppUser me = currentUser.get();
        FollowUp existing = followUps.getAccessible(id, me);
        form.setCustomerId(existing.getCustomer().getId());
        // The "today or later" rule applies only when the date is set or changed
        if (!Objects.equals(form.getDueDate(), existing.getDueDate())) rejectPastDate(form.getDueDate(), errors);
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
    public String complete(@PathVariable Long id, RedirectAttributes flash) {
        FollowUp f = followUps.complete(id, currentUser.get());
        flash.addFlashAttribute("success", "Follow-up marked as completed.");
        return "redirect:/customers/" + f.getCustomer().getId();
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes flash) {
        Long customerId = followUps.delete(id, currentUser.get());
        flash.addFlashAttribute("success", "Follow-up deleted.");
        return "redirect:/customers/" + customerId;
    }

    private static void rejectPastDate(LocalDate due, BindingResult errors) {
        if (due != null && due.isBefore(LocalDate.now())) {
            errors.rejectValue("dueDate", "past", "The due date must be today or later");
        }
    }

    private String formView(Model model, FollowUp existing, Long customerId) {
        AppUser me = currentUser.get();
        model.addAttribute("followUp", existing);
        model.addAttribute("customerOptions", customers.listInScope(me));
        model.addAttribute("today", LocalDate.now());
        if (customerId != null) {
            Customer c = customers.getAccessible(customerId, me);
            model.addAttribute("customer", c);
            model.addAttribute("activityOptions", activities.timeline(c.getId()));
        }
        return "followups/form";
    }
}
