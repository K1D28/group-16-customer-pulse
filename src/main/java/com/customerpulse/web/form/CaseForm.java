package com.customerpulse.web.form;

import com.customerpulse.domain.CaseAssignment;
import com.customerpulse.domain.CasePriority;
import com.customerpulse.domain.ServiceCase;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class CaseForm {

    @NotBlank(message = "Title is required")
    @Size(max = 150, message = "Up to 150 characters")
    private String title;

    @Size(max = 2000, message = "Up to 2000 characters")
    private String description;

    @NotNull(message = "Choose a customer")
    private Long customerId;

    @NotNull(message = "Choose a priority")
    private CasePriority priority = CasePriority.MEDIUM;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate dueDate;

    @NotEmpty(message = "Assign at least one employee")
    private List<Long> assigneeIds = new ArrayList<>();

    public static CaseForm from(ServiceCase c, List<CaseAssignment> assignments) {
        CaseForm f = new CaseForm();
        f.title = c.getTitle();
        f.description = c.getDescription();
        f.customerId = c.getCustomer().getId();
        f.priority = c.getPriority();
        f.dueDate = c.getDueDate();
        f.assigneeIds = new ArrayList<>(assignments.stream().map(a -> a.getUser().getId()).toList());
        return f;
    }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Long getCustomerId() { return customerId; }
    public void setCustomerId(Long customerId) { this.customerId = customerId; }
    public CasePriority getPriority() { return priority; }
    public void setPriority(CasePriority priority) { this.priority = priority; }
    public LocalDate getDueDate() { return dueDate; }
    public void setDueDate(LocalDate dueDate) { this.dueDate = dueDate; }
    public List<Long> getAssigneeIds() { return assigneeIds; }
    public void setAssigneeIds(List<Long> assigneeIds) { this.assigneeIds = assigneeIds; }
}
