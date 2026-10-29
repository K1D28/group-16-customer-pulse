package com.customerpulse.web;

import com.customerpulse.domain.*;
import com.customerpulse.repository.CustomerRepository;
import com.customerpulse.security.CurrentUser;
import com.customerpulse.service.*;
import com.customerpulse.web.form.CaseForm;
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
@RequestMapping("/cases")
public class CaseController {

    private final CurrentUser currentUser;
    private final CaseService cases;
    private final ActivityService activities;
    private final UserService users;
    private final CustomerRepository customers;
    private final AccessGuard guard;

    public CaseController(CurrentUser currentUser, CaseService cases, ActivityService activities, UserService users,
                          CustomerRepository customers, AccessGuard guard) {
        this.currentUser = currentUser;
        this.cases = cases;
        this.activities = activities;
        this.users = users;
        this.customers = customers;
        this.guard = guard;
    }

    /** Managers see every case; employees see the cases they are assigned to. */
    @GetMapping
    public String list(@RequestParam(defaultValue = "active") String tab, Model model) {
        AppUser me = currentUser.get();
        Set<CaseStatus> statuses = switch (tab) {
            case "closed" -> EnumSet.of(CaseStatus.CLOSED);
            case "all" -> EnumSet.allOf(CaseStatus.class);
            default -> CaseService.ACTIVE;
        };
        List<ServiceCase> list = cases.list(me, statuses);
        model.addAttribute("items", list);
        model.addAttribute("assignees", cases.assignmentsFor(list));
        model.addAttribute("tab", tab);
        model.addAttribute("activeCount", cases.list(me, CaseService.ACTIVE).size());
        model.addAttribute("closedCount", cases.list(me, EnumSet.of(CaseStatus.CLOSED)).size());
        return "cases/list";
    }

    @GetMapping("/new")
    public String newForm(@RequestParam(required = false) Long customerId, Model model) {
        guard.checkManager(currentUser.get());
        CaseForm form = new CaseForm();
        form.setCustomerId(customerId);
        model.addAttribute("form", form);
        return formView(model, null);
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("form") CaseForm form, BindingResult errors,
                         Model model, RedirectAttributes flash) {
        AppUser me = currentUser.get();
        guard.checkManager(me);
        if (errors.hasErrors()) return formView(model, null);
        ServiceCase c = cases.create(form, me);
        flash.addFlashAttribute("success", c.getCode() + " created and assigned to "
                + form.getAssigneeIds().size() + " employee(s).");
        return "redirect:/cases/" + c.getId();
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model) {
        AppUser me = currentUser.get();
        ServiceCase c = cases.getViewable(id, me);
        List<CaseAssignment> team = cases.assignments(id);
        CaseAssignment mine = team.stream().filter(a -> a.getUser().getId().equals(me.getId())).findFirst().orElse(null);
        model.addAttribute("serviceCase", c);
        model.addAttribute("team", team);
        model.addAttribute("mine", mine);
        model.addAttribute("progress", CaseService.progress(team));
        model.addAttribute("caseActivities", activities.forCase(id));
        model.addAttribute("canLog", cases.canLog(c, me));
        model.addAttribute("canViewCustomer", guard.canAccess(me, c.getCustomer()));
        return "cases/detail";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        AppUser me = currentUser.get();
        guard.checkManager(me);
        ServiceCase c = cases.getViewable(id, me);
        model.addAttribute("form", CaseForm.from(c, cases.assignments(id)));
        return formView(model, c);
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id, @Valid @ModelAttribute("form") CaseForm form, BindingResult errors,
                         Model model, RedirectAttributes flash) {
        AppUser me = currentUser.get();
        guard.checkManager(me);
        ServiceCase existing = cases.getViewable(id, me);
        if (errors.hasErrors()) return formView(model, existing);
        cases.update(id, form, me);
        flash.addFlashAttribute("success", "Case updated.");
        return "redirect:/cases/" + id;
    }

    @PostMapping("/{id}/done")
    public String done(@PathVariable Long id, @RequestParam(required = false) String note, RedirectAttributes flash) {
        try {
            ServiceCase c = cases.markDone(id, note, currentUser.get());
            flash.addFlashAttribute("success", c.isClosed()
                    ? "Your part is done. Everyone has finished, so " + c.getCode() + " is now closed."
                    : "Your part is marked as done.");
        } catch (BusinessException e) {
            flash.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/cases/" + id;
    }

    @PostMapping("/{id}/undo")
    public String undo(@PathVariable Long id, RedirectAttributes flash) {
        try {
            cases.undoDone(id, currentUser.get());
            flash.addFlashAttribute("info", "Your part is back to in progress.");
        } catch (BusinessException e) {
            flash.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/cases/" + id;
    }

    @PostMapping("/{id}/close")
    public String close(@PathVariable Long id, RedirectAttributes flash) {
        cases.close(id, currentUser.get());
        flash.addFlashAttribute("success", "Case closed.");
        return "redirect:/cases/" + id;
    }

    @PostMapping("/{id}/reopen")
    public String reopen(@PathVariable Long id, RedirectAttributes flash) {
        cases.reopen(id, currentUser.get());
        flash.addFlashAttribute("success", "Case reopened. Each assignee needs to mark their part done again.");
        return "redirect:/cases/" + id;
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes flash) {
        AppUser me = currentUser.get();
        String code = cases.getViewable(id, me).getCode();
        cases.delete(id, me);
        flash.addFlashAttribute("success", code + " deleted. Its activities stay on the customer timeline.");
        return "redirect:/cases";
    }

    private String formView(Model model, ServiceCase existing) {
        model.addAttribute("serviceCase", existing);
        AppUser me = currentUser.get();
        model.addAttribute("customerOptions", customers.findInScope(null, me.getTeam().getId()));
        model.addAttribute("userOptions", users.activeTeam(me));
        model.addAttribute("priorities", CasePriority.values());
        return "cases/form";
    }
}
