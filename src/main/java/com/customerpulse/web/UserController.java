package com.customerpulse.web;

import com.customerpulse.domain.AppUser;
import com.customerpulse.repository.CustomerRepository;
import com.customerpulse.security.CurrentUser;
import com.customerpulse.service.BusinessException;
import com.customerpulse.service.UserService;
import com.customerpulse.web.form.UserForm;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.HashMap;
import java.util.Map;

/** UI-06 / FR-10: Manager-only (secured in SecurityConfig). */
@Controller
@RequestMapping("/users")
public class UserController {

    private final CurrentUser currentUser;
    private final UserService users;
    private final CustomerRepository customers;

    public UserController(CurrentUser currentUser, UserService users, CustomerRepository customers) {
        this.currentUser = currentUser;
        this.users = users;
        this.customers = customers;
    }

    @GetMapping
    public String list(Model model) {
        var all = users.team(currentUser.get());
        Map<Long, Long> owned = new HashMap<>();
        for (AppUser u : all) owned.put(u.getId(), customers.countByOwnerId(u.getId()));
        model.addAttribute("users", all);
        model.addAttribute("owned", owned);
        return "users/list";
    }

    @GetMapping("/new")
    public String newForm(Model model) {
        model.addAttribute("form", new UserForm());
        model.addAttribute("editUser", null);
        return "users/form";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("form") UserForm form, BindingResult errors,
                         Model model, RedirectAttributes flash) {
        checkPassword(form, errors, true);
        if (!errors.hasFieldErrors("email") && users.emailTaken(form.getEmail(), null)) {
            errors.rejectValue("email", "taken", "That email is already in use.");
        }
        if (errors.hasErrors()) {
            model.addAttribute("editUser", null);
            return "users/form";
        }
        AppUser u = users.create(form, currentUser.get());
        flash.addFlashAttribute("success", "User " + u.getFullName() + " created.");
        return "redirect:/users";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        AppUser u = users.get(id, currentUser.get());
        model.addAttribute("form", UserForm.from(u));
        model.addAttribute("editUser", u);
        return "users/form";
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id, @Valid @ModelAttribute("form") UserForm form, BindingResult errors,
                         Model model, RedirectAttributes flash) {
        AppUser u = users.get(id, currentUser.get());
        checkPassword(form, errors, false);
        if (!errors.hasFieldErrors("email") && users.emailTaken(form.getEmail(), id)) {
            errors.rejectValue("email", "taken", "That email is already in use.");
        }
        if (!errors.hasErrors()) {
            try {
                users.update(id, form, currentUser.get());
                flash.addFlashAttribute("success", "User " + form.getFullName() + " saved.");
                return "redirect:/users";
            } catch (BusinessException e) {
                model.addAttribute("error", e.getMessage());
            }
        }
        model.addAttribute("editUser", u);
        return "users/form";
    }

    @PostMapping("/{id}/deactivate")
    public String deactivate(@PathVariable Long id, RedirectAttributes flash) {
        try {
            AppUser u = users.setActive(id, false, currentUser.get());
            flash.addFlashAttribute("success", u.getFullName() + " is deactivated and can no longer log in.");
        } catch (BusinessException e) {
            flash.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/users";
    }

    @PostMapping("/{id}/activate")
    public String activate(@PathVariable Long id, RedirectAttributes flash) {
        AppUser u = users.setActive(id, true, currentUser.get());
        flash.addFlashAttribute("success", u.getFullName() + " is active again.");
        return "redirect:/users";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes flash) {
        try {
            String name = users.get(id, currentUser.get()).getFullName();
            users.delete(id, currentUser.get());
            flash.addFlashAttribute("success", "User " + name + " deleted.");
        } catch (BusinessException e) {
            flash.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/users";
    }

    private static void checkPassword(UserForm form, BindingResult errors, boolean required) {
        String p = form.getPassword();
        if (p == null || p.isBlank()) {
            if (required) errors.rejectValue("password", "required", "An initial password is required");
        } else if (p.length() < UserService.MIN_PASSWORD) {
            errors.rejectValue("password", "short", "Use at least " + UserService.MIN_PASSWORD + " characters");
        }
    }
}
