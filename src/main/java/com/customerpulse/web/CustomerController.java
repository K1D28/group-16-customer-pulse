package com.customerpulse.web;

import com.customerpulse.domain.AppUser;
import com.customerpulse.domain.Customer;
import com.customerpulse.domain.CustomerStatus;
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

import java.util.List;

@Controller
@RequestMapping("/customers")
public class CustomerController {

    private final CurrentUser currentUser;
    private final CustomerService customers;
    private final ActivityService activities;
    private final FollowUpService followUps;
    private final UserService users;
    private final com.customerpulse.repository.StatusHistoryRepository history;
    private final CaseService cases;

    public CustomerController(CurrentUser currentUser, CustomerService customers, ActivityService activities,
                              FollowUpService followUps, UserService users,
                              com.customerpulse.repository.StatusHistoryRepository history, CaseService cases) {
        this.cases = cases;
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

    @GetMapping("/new")
    public String newForm(Model model) {
        AppUser me = currentUser.get();
        CustomerForm form = new CustomerForm();
        form.setOwnerId(me.getId());
        model.addAttribute("form", form);
        return formView(model, null);
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("form") CustomerForm form, BindingResult errors,
                         Model model, RedirectAttributes flash) {
        AppUser me = currentUser.get();
        if (errors.hasErrors()) return formView(model, null);
        if (!form.isConfirmDuplicate()) {
            List<Customer> similar = customers.findSimilar(form.getCompanyName(), null, me);
            if (!similar.isEmpty()) {
                model.addAttribute("similar", similar);
                return formView(model, null);
            }
        }
        Customer c = customers.create(form, me);
        flash.addFlashAttribute("success", "Customer \"" + c.getCompanyName() + "\" created as Negotiating.");
        return "redirect:/customers/" + c.getId();
    }

    /** UI-04 / FR-03: profile, timeline, follow-ups and status history. */
    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model) {
        AppUser me = currentUser.get();
        Customer c = customers.getAccessible(id, me);
        model.addAttribute("customer", c);
        model.addAttribute("timeline", activities.timeline(id));
        model.addAttribute("followUps", followUps.forCustomer(id));
        model.addAttribute("history", history.findByCustomerIdOrderByChangedAtDesc(id));
        var customerCases = cases.forCustomer(id);
        model.addAttribute("customerCases", customerCases);
        model.addAttribute("caseTeams", cases.assignmentsFor(customerCases));
        if (me.isManager()) model.addAttribute("users", users.activeTeam(me));
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
        if (errors.hasErrors()) return formView(model, existing);
        if (!form.isConfirmDuplicate()) {
            List<Customer> similar = customers.findSimilar(form.getCompanyName(), id, me);
            boolean nameChanged = !form.getCompanyName().trim().equalsIgnoreCase(existing.getCompanyName());
            if (nameChanged && !similar.isEmpty()) {
                model.addAttribute("similar", similar);
                return formView(model, existing);
            }
        }
        customers.update(id, form, me);
        flash.addFlashAttribute("success", "Customer details saved.");
        return "redirect:/customers/" + id;
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes flash) {
        AppUser me = currentUser.get();
        String name = customers.getAccessible(id, me).getCompanyName();
        customers.delete(id, me);
        flash.addFlashAttribute("success", "Deleted \"" + name + "\" and all of its records.");
        return "redirect:/customers";
    }

    /** FR-06. */
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

    /** FR-09. */
    @PostMapping("/{id}/owner")
    public String reassign(@PathVariable Long id, @RequestParam Long ownerId,
                           @RequestParam(defaultValue = "false") boolean moveFollowUps, RedirectAttributes flash) {
        try {
            Customer c = customers.reassign(id, ownerId, moveFollowUps, currentUser.get());
            flash.addFlashAttribute("success", "Owner changed to " + c.getOwner().getFullName()
                    + (moveFollowUps ? ". Open follow-ups moved to them too." : "."));
        } catch (BusinessException e) {
            flash.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/customers/" + id;
    }

    private String formView(Model model, Customer existing) {
        model.addAttribute("customer", existing);
        model.addAttribute("users", users.activeTeam(currentUser.get()));
        return "customers/form";
    }
}
