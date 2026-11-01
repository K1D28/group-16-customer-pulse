package com.customerpulse.web;

import com.customerpulse.domain.AppUser;
import com.customerpulse.repository.UserRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.Comparator;
import java.util.List;

@Controller
public class AuthController {

    /** Demo accounts all end with this domain and use the demo password. */
    private static final String DEMO_DOMAIN = "@example.com";

    private final UserRepository users;

    public AuthController(UserRepository users) {
        this.users = users;
    }

    /** UI-01. Lists the active demo accounts (Managers first) as one-click logins. */
    @GetMapping("/login")
    public String login(Model model) {
        List<AppUser> demo = users.findByActiveTrueOrderByFullNameAsc().stream()
                .filter(u -> u.getEmail().endsWith(DEMO_DOMAIN))
                .sorted(Comparator.comparing((AppUser u) -> !u.isManager()).thenComparing(AppUser::getFullName))
                .toList();
        model.addAttribute("demoAccounts", demo);
        return "login";
    }

    @GetMapping("/")
    public String home() {
        return "redirect:/dashboard";
    }
}
