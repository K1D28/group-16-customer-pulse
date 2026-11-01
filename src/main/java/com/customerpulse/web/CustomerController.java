package com.customerpulse.web;

import com.customerpulse.domain.AppUser;
import com.customerpulse.domain.Customer;
import com.customerpulse.domain.CustomerStatus;
import com.customerpulse.repository.StatusHistoryRepository;
import com.customerpulse.security.CurrentUser;
import com.customerpulse.service.*;
import com.customerpulse.web.form.CustomerForm;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/customers")
public class CustomerController {

    private final CurrentUser currentUser;
    private final CustomerService customers;
    private final ActivityService activities;
    private final FollowUpService followUps;
    private final UserService users;
    private final StatusHistoryRepository history;

    public CustomerController(CurrentUser currentUser, CustomerService customers, ActivityService activities,
                              FollowUpService followUps, UserService users, StatusHistoryRepository history) {
        this.currentUser = currentUser;
        this.customers = customers;
        this.activities = activities;
        this.followUps = followUps;
        this.users = users;
        this.history = history;
    }

    /** UI-03 / FR-11: list and search, 10 per page. */
    @GetMapping
    public String list(@RequestParam(required = false) String q,
                       @RequestParam(required = false) CustomerStatus status,
                       @RequestParam(defaultValue = "0") int page,
                       Model model) {
        AppUser me = currentUser.get();
        Page<Customer> result = customers.search(me, q, status, page);
        model.addAttribute("page", result);
        model.addAttribute("q", q);
        model.addAttribute("status", status);
        model.addAttribute("lastActivity", customers.lastActivityByCustomer());
        return "customers/list";
    }

    /** UI-04: add customer. */
    @GetMapping("/new")
    public String newForm(Model model) {
        CustomerForm form = new CustomerForm();
        form.setOwnerId(currentUser.get().getId());
        model.addAttribute("form", form);
        return formView(model, null);
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("form") CustomerForm form, BindingResult errors,
                         Model model, RedirectAttributes flash) {
        AppUser me = currentUser.get();
        if (errors.hasErrors() || !duplicateCheckPasses(form, null, errors, model)) return formView(model, null);
        try {
            Customer c = customers.create(form, me);
            flash.addFlashAttribute("success", "Customer \"" + c.getCompanyName() + "\" created as Negotiating.");
            return "redirect:/customers/" + c.getId();
        } catch (BusinessException e) {
            model.addAttribute("error", e.getMessage());
            return formView(model, null);
        }
    }

    /** UI-05 / FR-03: profile, open follow-ups, status history and timeline. */
    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model) {
        AppUser me = currentUser.get();
        Customer c = customers.getAccessible(id, me);
        model.addAttribute("customer", c);
        model.addAttribute("timeline", activities.timeline(id));
        model.addAttribute("followUps", followUps.forCustomer(id));
        model.addAttribute("history", history.findByCustomerIdOrderByChangedAtDescIdDesc(id));
        if (me.isManager()) {
            model.addAttribute("users", users.active());
            model.addAttribute("openFollowUps", customers.openFollowUpCount(id));
        }
        return "customers/detail";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        Customer c = customers.getAccessible(id, currentUser.get());
        model.addAttribute("form", CustomerForm.from(c));
        return formView(model, c);
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id, @Valid @ModelAttribute("form") CustomerForm form, BindingResult errors,
                         Model model, RedirectAttributes flash) {
        AppUser me = currentUser.get();
        Customer existing = customers.getAccessible(id, me);
        boolean nameChanged = !errors.hasFieldErrors("companyName")
                && !form.getCompanyName().trim().equalsIgnoreCase(existing.getCompanyName());
        if (errors.hasErrors() || (nameChanged && !duplicateCheckPasses(form, id, errors, model))) {
            return formView(model, existing);
        }
        try {
            customers.update(id, form, me);
        } catch (BusinessException e) {
            model.addAttribute("error", e.getMessage());
            return formView(model, existing);
        }
        flash.addFlashAttribute("success", "Customer details saved.");
        return "redirect:/customers/" + id;
    }

    /** FR-02: soft delete after confirmation. */
    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes flash) {
        Customer c = customers.delete(id, currentUser.get());
        flash.addFlashAttribute("success", "Customer \"" + c.getCompanyName() + "\" deleted.");
        return "redirect:/customers";
    }

    /** UI-08 / FR-06. */
    @PostMapping("/{id}/status")
    public String changeStatus(@PathVariable Long id, @RequestParam CustomerStatus newStatus,
                               @RequestParam(required = false) String reason, RedirectAttributes flash) {
        try {
            boolean changed = customers.changeStatus(id, newStatus, reason, currentUser.get());
            if (changed) flash.addFlashAttribute("success", "Status changed to " + newStatus.getLabel() + ".");
            else flash.addFlashAttribute("info", "The customer is already " + newStatus.getLabel() + ". Nothing was saved.");
        } catch (BusinessException e) {
            flash.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/customers/" + id;
    }

    /** UI-09 / FR-09: Manager only; open follow-ups always move with the customer. */
    @PostMapping("/{id}/owner")
    public String reassign(@PathVariable Long id, @RequestParam(required = false) Long ownerId, RedirectAttributes flash) {
        try {
            int moved = customers.reassign(id, ownerId, currentUser.get());
            Customer c = customers.getAccessible(id, currentUser.get());
            flash.addFlashAttribute("success", "Owner changed to " + c.getOwner().getFullName() + ". "
                    + moved + (moved == 1 ? " open follow-up" : " open follow-ups") + " moved to them.");
        } catch (BusinessException e) {
            flash.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/customers/" + id;
    }

    /**
     * FR-12: an exact match (ignoring upper/lower case) blocks saving; similar names show a warning
     * until the user chooses "Save anyway". Only company name and owner name are shown.
     */
    private boolean duplicateCheckPasses(CustomerForm form, Long excludeId, BindingResult errors, Model model) {
        CustomerService.DuplicateCheck check = customers.checkDuplicates(form.getCompanyName(), excludeId);
        if (check.blocked()) {
            errors.rejectValue("companyName", "duplicate", CustomerService.duplicateMessage(check.exact()));
            return false;
        }
        if (!check.similar().isEmpty() && !form.isConfirmDuplicate()) {
            model.addAttribute("similar", check.similar());
            return false;
        }
        return true;
    }

    private String formView(Model model, Customer existing) {
        model.addAttribute("customer", existing);
        if (existing == null) model.addAttribute("users", users.active());
        return "customers/form";
    }
}
