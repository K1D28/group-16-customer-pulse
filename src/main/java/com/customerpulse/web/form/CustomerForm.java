package com.customerpulse.web.form;

import com.customerpulse.domain.Customer;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public class CustomerForm {

    @NotBlank(message = "Company name is required")
    @Size(max = 150, message = "Up to 150 characters")
    private String companyName;

    @Size(max = 100, message = "Up to 100 characters")
    private String contactName;

    @Email(message = "Enter a valid email address")
    @Size(max = 100, message = "Up to 100 characters")
    private String email;

    @Pattern(regexp = "^$|^[0-9]{10}$", message = "Enter a 10-digit phone number (numbers only)")
    private String phone;

    @DecimalMin(value = "0.00", message = "Budget cannot be negative")
    @Digits(integer = 13, fraction = 2, message = "Use up to 13 digits and 2 decimal places")
    private BigDecimal projectBudget;

    /** Only used on create, and only by Managers; Staff always own what they create. Later changes use FR-09. */
    private Long ownerId;

    /** Set when the user chose "Save anyway" after the similar-name warning (FR-12). */
    private boolean confirmDuplicate;

    public static CustomerForm from(Customer c) {
        CustomerForm f = new CustomerForm();
        f.companyName = c.getCompanyName();
        f.contactName = c.getContactName();
        f.email = c.getEmail();
        f.phone = c.getPhone();
        f.projectBudget = c.getProjectBudget();
        f.ownerId = c.getOwner().getId();
        return f;
    }

    public String getCompanyName() { return companyName; }
    public void setCompanyName(String companyName) { this.companyName = companyName; }
    public String getContactName() { return contactName; }
    public void setContactName(String contactName) { this.contactName = contactName; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public BigDecimal getProjectBudget() { return projectBudget; }
    public void setProjectBudget(BigDecimal projectBudget) { this.projectBudget = projectBudget; }
    public Long getOwnerId() { return ownerId; }
    public void setOwnerId(Long ownerId) { this.ownerId = ownerId; }
    public boolean isConfirmDuplicate() { return confirmDuplicate; }
    public void setConfirmDuplicate(boolean confirmDuplicate) { this.confirmDuplicate = confirmDuplicate; }
}
