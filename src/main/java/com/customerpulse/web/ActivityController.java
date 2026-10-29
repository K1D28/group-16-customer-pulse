package com.customerpulse.web;

import com.customerpulse.domain.*;
import com.customerpulse.security.CurrentUser;
import com.customerpulse.service.ActivityService;
import com.customerpulse.service.CaseService;
import com.customerpulse.service.CustomerService;
import org.springframework.security.access.AccessDeniedException;
import com.customerpulse.web.form.ActivityForm;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/activities")
public class ActivityController {

    private final CurrentUser currentUser;
    private final ActivityService activities;
    private final CustomerService customers;
    private final CaseService cases;

    public ActivityController(CurrentUser currentUser, ActivityService activities, CustomerService customers,
                              CaseService cases) {
        this.currentUser = currentUser;
        this.activities = activities;
        this.customers = customers;
        this.cases = cases;
    }

    /** Activity log across all customers in scope. */
    @GetMapping
    public String list(@RequestParam(required = false) ActivityType type,
                       @RequestParam(required = false) Channel channel,
                       @RequestParam(required = false) String q,
                       @RequestParam(defaultValue = "0") int page,
                       Model model) {
        model.addAttribute("page", activities.search(currentUser.get(), type, channel, q, page));
        model.addAttribute("type", type);
        model.addAttribute("channel", channel);
        model.addAttribute("q", q);
        return "activities/list";
    }

    /** UI-05: log activity, optionally for a pre-selected customer. */
    @GetMapping("/new")
    public String newForm(@RequestParam(required = false) Long customerId,
                          @RequestParam(required = false) Long caseId, Model model) {
        AppUser me = currentUser.get();
        ActivityForm form = new ActivityForm();
        if (caseId != null) {
            ServiceCase sc = cases.getViewable(caseId, me);
            if (!cases.canLog(sc, me)) throw new AccessDeniedException("You cannot log activity on this case.");
            form.setCaseId(caseId);
            form.setCustomerId(sc.getCustomer().getId());
        } else if (customerId != null) {
            customers.getAccessible(customerId, me);
            form.setCustomerId(customerId);
        }
        model.addAttribute("form", form);
        return formView(model, null);
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("form") ActivityForm form, BindingResult errors,
                         Model model, RedirectAttributes flash) {
        if (errors.hasErrors()) return formView(model, null);
        Activity a = activities.log(form, currentUser.get());
        flash.addFlashAttribute("success", "Activity logged.");
        if (a.getServiceCase() != null) return "redirect:/cases/" + a.getServiceCase().getId();
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
        form.setCaseId(existing.getServiceCase() == null ? null : existing.getServiceCase().getId());
        if (errors.hasErrors()) return formView(model, existing);
        activities.update(id, form, me);
        flash.addFlashAttribute("success", "Activity updated.");
        if (form.getCaseId() != null) return "redirect:/cases/" + form.getCaseId();
        return "redirect:/customers/" + existing.getCustomer().getId();
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, @RequestParam(required = false) String back, RedirectAttributes flash) {
        Long customerId = activities.delete(id, currentUser.get());
        flash.addFlashAttribute("success", "Activity deleted.");
        if (back != null && back.startsWith("/") && !back.startsWith("//")) return "redirect:" + back;
        return "redirect:/customers/" + customerId;
    }

    private String formView(Model model, Activity existing) {
        ActivityForm form = (ActivityForm) model.getAttribute("form");
        if (form != null && form.getCaseId() != null) {
            model.addAttribute("serviceCase", cases.getViewable(form.getCaseId(), currentUser.get()));
        }
        model.addAttribute("activity", existing);
        model.addAttribute("customerOptions", customers.listInScope(currentUser.get()));
        return "activities/form";
    }
}
