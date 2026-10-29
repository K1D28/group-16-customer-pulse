package com.customerpulse.web.form;

import com.customerpulse.domain.FollowUp;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

public class FollowUpForm {

    @NotNull(message = "Choose a customer")
    private Long customerId;

    /** Optional originating activity. */
    private Long activityId;

    @NotNull(message = "Choose who is responsible")
    private Long assignedUserId;

    @NotNull(message = "Due date is required")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate dueDate = LocalDate.now().plusDays(7);

    @Size(max = 255, message = "Up to 255 characters")
    private String notes;

    public static FollowUpForm from(FollowUp f) {
        FollowUpForm form = new FollowUpForm();
        form.customerId = f.getCustomer().getId();
        form.activityId = f.getActivity() == null ? null : f.getActivity().getId();
        form.assignedUserId = f.getAssignedUser().getId();
        form.dueDate = f.getDueDate();
        form.notes = f.getNotes();
        return form;
    }

    public Long getCustomerId() { return customerId; }
    public void setCustomerId(Long customerId) { this.customerId = customerId; }
    public Long getActivityId() { return activityId; }
    public void setActivityId(Long activityId) { this.activityId = activityId; }
    public Long getAssignedUserId() { return assignedUserId; }
    public void setAssignedUserId(Long assignedUserId) { this.assignedUserId = assignedUserId; }
    public LocalDate getDueDate() { return dueDate; }
    public void setDueDate(LocalDate dueDate) { this.dueDate = dueDate; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}
