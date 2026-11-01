package com.customerpulse.web.form;

import com.customerpulse.domain.Activity;
import com.customerpulse.domain.ActivityType;
import com.customerpulse.domain.Channel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

public class ActivityForm {

    @NotNull(message = "Choose a customer")
    private Long customerId;

    @NotNull(message = "Choose an activity type")
    private ActivityType activityType;

    @NotNull(message = "Choose a channel")
    private Channel channel;

    @NotBlank(message = "Outcome is required")
    @Size(max = 2000, message = "Up to 2000 characters")
    private String outcome;

    @NotNull(message = "Date and time are required")
    @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm")
    private LocalDateTime activityDate = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES);

    public static ActivityForm from(Activity a) {
        ActivityForm f = new ActivityForm();
        f.customerId = a.getCustomer().getId();
        f.activityType = a.getActivityType();
        f.channel = a.getChannel();
        f.outcome = a.getOutcome();
        f.activityDate = a.getActivityDate().truncatedTo(ChronoUnit.MINUTES);
        return f;
    }

    public Long getCustomerId() { return customerId; }
    public void setCustomerId(Long customerId) { this.customerId = customerId; }
    public ActivityType getActivityType() { return activityType; }
    public void setActivityType(ActivityType activityType) { this.activityType = activityType; }
    public Channel getChannel() { return channel; }
    public void setChannel(Channel channel) { this.channel = channel; }
    public String getOutcome() { return outcome; }
    public void setOutcome(String outcome) { this.outcome = outcome; }
    public LocalDateTime getActivityDate() { return activityDate; }
    public void setActivityDate(LocalDateTime activityDate) { this.activityDate = activityDate; }
}
