package com.customerpulse.web.form;

import com.customerpulse.domain.AppUser;
import com.customerpulse.domain.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class UserForm {

    @NotBlank(message = "Full name is required")
    @Size(max = 100, message = "Up to 100 characters")
    private String fullName;

    @NotBlank(message = "Email is required")
    @Email(message = "Enter a valid email address")
    @Size(max = 100, message = "Up to 100 characters")
    private String email;

    /** Required for new users; leave blank on edit to keep the current password. */
    private String password;

    @NotNull(message = "Choose a role")
    private Role role = Role.STAFF;

    public static UserForm from(AppUser u) {
        UserForm f = new UserForm();
        f.fullName = u.getFullName();
        f.email = u.getEmail();
        f.role = u.getRole();
        return f;
    }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }
}
