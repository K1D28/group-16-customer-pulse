package com.customerpulse.web;

import com.customerpulse.domain.Activity;
import com.customerpulse.domain.AppUser;
import com.customerpulse.security.CurrentUser;
import com.customerpulse.service.ActivityService;
import com.customerpulse.service.BusinessException;
import com.customerpulse.service.CustomerService;
import com.customerpulse.web.form.ActivityForm;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;

/** UI-06 / FR-04: log, edit and delete activities. */
@Controller
@RequestMapping("/activities")
public class ActivityController {

    private final CurrentUser currentUser;
    private final ActivityService activities;
    private final CustomerService customers;

    public ActivityController(CurrentUser currentUser, ActivityService activities, CustomerService customers) {
        this.currentUser = currentUser;
        this.activities = activities;
        this.customers = customers;
    }

    @GetMapping("/new")
    public String newForm(@RequestParam(required = false) Long customerId, Model model) {
        ActivityForm form = new ActivityForm();
        if (customerId != null) {
            customers.getAccessible(customerId, currentUser.get());
            form.setCustomerId(customerId);
        }
        model.addAttribute("form", form);
        return formView(model, null);
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("form") ActivityForm form, BindingResult errors,
                         Model model, RedirectAttributes flash) {
        rejectFutureDate(form, errors);
        if (errors.hasErrors()) return formView(model, null);
        Activity a;
        try {
            a = activities.log(form, currentUser.get());
        } catch (BusinessException e) {
            model.addAttribute("error", e.getMessage());
            return formView(model, null);
        }
        flash.addFlashAttribute("success", "Activity logged.");
        // FR-04 step 5: offer to create a follow-up for this activity
        flash.addFlashAttribute("offerFollowUpFor", a.getId());
        return "redirect:/customers/" + a.getCustomer().getId();
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        Activity a = activities.getEditable(id, currentUser.get());
        model.addAttribute("form", ActivityForm.from(a));
        return formView(model, a);
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id, @Valid @ModelAttribute("form") ActivityForm form,
                         BindingResult errors, Model model, RedirectAttributes flash) {
        AppUser me = currentUser.get();
        Activity existing = activities.getEditable(id, me);
        form.setCustomerId(existing.getCustomer().getId()); // an activity cannot move to another customer
        rejectFutureDate(form, errors);
        if (errors.hasErrors()) return formView(model, existing);
        try {
            activities.update(id, form, me);
        } catch (BusinessException e) {
            model.addAttribute("error", e.getMessage());
            return formView(model, existing);
        }
        flash.addFlashAttribute("success", "Activity updated.");
        return "redirect:/customers/" + existing.getCustomer().getId();
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes flash) {
        Long customerId = activities.delete(id, currentUser.get());
        flash.addFlashAttribute("success", "Activity deleted.");
        return "redirect:/customers/" + customerId;
    }

    /** UI-06: a future date is rejected, shown next to the field. */
    private static void rejectFutureDate(ActivityForm form, BindingResult errors) {
        if (form.getActivityDate() != null && form.getActivityDate().isAfter(LocalDateTime.now())) {
            errors.rejectValue("activityDate", "future", "The date cannot be in the future");
        }
    }

    private String formView(Model model, Activity existing) {
        model.addAttribute("activity", existing);
        model.addAttribute("customerOptions", customers.listInScope(currentUser.get()));
        return "activities/form";
    }
}
