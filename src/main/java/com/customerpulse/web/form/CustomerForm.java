package com.customerpulse.web.form;

import com.customerpulse.domain.Customer;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class CustomerForm {

    @NotBlank(message = "Company name is required")
    @Size(max = 150, message = "Up to 150 characters")
    private String companyName;

    @Size(max = 100, message = "Up to 100 characters")
    private String contactName;

    @Email(message = "Enter a valid email address")
    @Size(max = 100, message = "Up to 100 characters")
    private String email;

    @Size(max = 30, message = "Up to 30 characters")
    @Pattern(regexp = "^$|^[+0-9 ()\\-]{6,30}$", message = "Use digits, spaces, +, - or ()")
    private String phone;

    /** Only Managers may choose; Staff always own what they create. */
    private Long ownerId;

    /** Set when the user chose "Save anyway" after the similar-name warning. */
    private boolean confirmDuplicate;

    public static CustomerForm from(Customer c) {
        CustomerForm f = new CustomerForm();
        f.companyName = c.getCompanyName();
        f.contactName = c.getContactName();
        f.email = c.getEmail();
        f.phone = c.getPhone();
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
    public Long getOwnerId() { return ownerId; }
    public void setOwnerId(Long ownerId) { this.ownerId = ownerId; }
    public boolean isConfirmDuplicate() { return confirmDuplicate; }
    public void setConfirmDuplicate(boolean confirmDuplicate) { this.confirmDuplicate = confirmDuplicate; }
}
